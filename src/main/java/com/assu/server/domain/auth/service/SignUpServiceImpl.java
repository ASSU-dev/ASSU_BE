package com.assu.server.domain.auth.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.auth.dto.signup.*;
import com.assu.server.domain.auth.dto.signup.common.CommonInfoPayloadDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;
import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.SSUAuthRepository;
import com.assu.server.domain.auth.security.adapter.RealmAuthAdapter;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.user.entity.Student;
import com.assu.server.domain.user.entity.enums.EnrollmentStatus;
import com.assu.server.domain.user.entity.enums.University;
import com.assu.server.domain.user.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.s3.AmazonS3Manager;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class SignUpServiceImpl implements SignUpService {

    private final MemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final PartnerRepository partnerRepository;
    private final AdminRepository adminRepository;

    private final List<RealmAuthAdapter> realmAuthAdapters;

    private final AmazonS3Manager amazonS3Manager;
    private final JwtUtil jwtUtil;

    private final GeometryFactory geometryFactory;
    private final StoreRepository storeRepository;
    private final SSUAuthService ssuAuthService;
    private final SSUAuthRepository ssuAuthRepository;

    private RealmAuthAdapter pickAdapter(AuthRealm realm) {
        return realmAuthAdapters.stream()
                .filter(a -> a.supports(realm))
                .findFirst()
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION));
    }

    @Override
    public SignUpResponseDTO signupSsuStudent(StudentTokenSignUpRequestDTO req) {
        if (memberRepository.existsByPhoneNum(req.phoneNumber())) {
            throw new CustomAuthException(ErrorStatus.EXISTED_PHONE);
        }

        // 1) 유세인트 인증 및 학생 정보 추출
        USaintAuthRequestDTO authRequest = new USaintAuthRequestDTO(
                req.studentTokenAuth().sToken(),
                req.studentTokenAuth().sIdno()
        );

        USaintAuthResponseDTO authResponse = ssuAuthService.uSaintAuth(authRequest);

        if (ssuAuthRepository.existsByStudentNumber(authResponse.studentNumber())) {
            throw new CustomAuthException(ErrorStatus.EXISTED_STUDENT);
        }

        // 2) member 생성
        Member member = memberRepository.save(
                Member.builder()
                        .phoneNum(req.phoneNumber())
                        .isPhoneVerified(true)
                        .isLocationTermAgreed(req.locationAgree())
                        .isMarketingTermAgreed(req.marketingAgree())
                        .role(UserRole.STUDENT)
                        .isActivated(ActivationStatus.ACTIVE)
                        .build());

        // 3) SSUAuth 생성 (학번만 저장)
        RealmAuthAdapter adapter = pickAdapter(AuthRealm.SSU);
        adapter.registerCredentials(member, authResponse.studentNumber(), ""); // 더미 패스워드

        // 4) Student 프로필 생성 (크롤링된 정보 사용)
        Student student = Student.builder()
                .member(member)
                .name(authResponse.name())
                .department(authResponse.major().getDepartment())
                .major(authResponse.major())
                .enrollmentStatus(parseEnrollmentStatus(authResponse.enrollmentStatus()))
                .yearSemester(authResponse.yearSemester())
                .university(University.SSU) // Todo: 추후 다른 대학도 추가할 시 로직 변경 필요
                .stamp(0)
                .build();

        studentRepository.save(student);

        // 5) JWT 토큰 발급
        TokensDTO tokens = jwtUtil.issueTokens(
                member.getId(),
                authResponse.studentNumber(),
                UserRole.STUDENT,
                "SSU");

        // 6) SignUpResponseDTO 생성
        return SignUpResponseDTO.from(member, tokens);
    }

    @Override
    public SignUpResponseDTO signupPartner(PartnerSignUpRequestDTO req, MultipartFile licenseImage) {
        if (memberRepository.existsByPhoneNum(req.phoneNumber())) {
            throw new CustomAuthException(ErrorStatus.EXISTED_PHONE);
        }

        // 1) member 생성
        Member member = memberRepository.save(
                Member.builder()
                        .phoneNum(req.phoneNumber())
                        .isPhoneVerified(true)
                        .isLocationTermAgreed(req.locationAgree())
                        .isMarketingTermAgreed(req.marketingAgree())
                        .role(UserRole.PARTNER)
                        .isActivated(ActivationStatus.ACTIVE) // Todo 초기에 SUSPEND 로직 추가해야함, 허가 후 ACTIVE
                        .build());

        // 2) RealmAuthAdapter 로 Common 자격 저장
        RealmAuthAdapter adapter = pickAdapter(AuthRealm.COMMON);
        adapter.registerCredentials(member, req.commonAuth().email(), req.commonAuth().password());

        String keyPath = "partners/" + member.getId() + "/" + licenseImage.getOriginalFilename();
        String keyName = amazonS3Manager.generateKeyName(keyPath);
        String licenseUrl = amazonS3Manager.uploadFile(keyName, licenseImage);
        CommonInfoPayloadDTO info = req.commonInfo();
        var sp = Optional.ofNullable(info.selectedPlace())
                .orElseThrow(() -> new CustomAuthException(ErrorStatus._BAD_REQUEST)); // selectedPlace 필수

        String address = pickDisplayAddress(sp.getRoadAddress(), sp.getAddress());
        Double lat = sp.getLatitude();
        Double lng = sp.getLongitude();
        Point point = toPoint(lat, lng);

        // 3) Partner 프로필 생성
        Partner partner = partnerRepository.save(
                Partner.builder()
                        .member(member)
                        .name(info.name())
                        .address(address)
                        .detailAddress(info.detailAddress())
                        .licenseUrl(licenseUrl)
                        .point(point)
                        .latitude(lat)
                        .longitude(lng)
                        .build());

        // store 생성/연결
        Optional<Store> storeOpt = storeRepository.findBySameAddress(address, info.detailAddress());
        if (storeOpt.isPresent()) {
            Store store = storeOpt.get();
            store.linkPartner(partner);
            store.setName(info.name());
            store.setGeo(lat, lng, point);
            storeRepository.save(store);
        } else {
            Store newly = Store.builder()
                    .partner(partner)
                    .rate(0)
                    .isActivate(ActivationStatus.ACTIVE)
                    .name(info.name())
                    .address(address)
                    .detailAddress(info.detailAddress())
                    .latitude(lat)
                    .longitude(lng)
                    .point(point)
                    .build();
            storeRepository.save(newly);
        }

        // 4) 토큰 발급
        TokensDTO tokens = jwtUtil.issueTokens(
                member.getId(),
                req.commonAuth().email(),
                UserRole.PARTNER,
                adapter.authRealmValue()
        );

        // 5) SignUpResponseDTO 생성
        return SignUpResponseDTO.from(member, tokens);
    }

    @Override
    public SignUpResponseDTO signupAdmin(AdminSignUpRequestDTO req, MultipartFile signImage) {
        if (memberRepository.existsByPhoneNum(req.phoneNumber())) {
            throw new CustomAuthException(ErrorStatus.EXISTED_PHONE);
        }

        // 1) member 생성
        Member member = memberRepository.save(
                Member.builder()
                        .phoneNum(req.phoneNumber())
                        .isPhoneVerified(true)
                        .isLocationTermAgreed(req.locationAgree())
                        .isMarketingTermAgreed(req.marketingAgree())
                        .role(UserRole.ADMIN)
                        .isActivated(ActivationStatus.ACTIVE) // Todo 초기에 SUSPEND 로직 추가해야함, 허가 후 ACTIVE
                        .build());

        // 2) RealmAuthAdapter 로 Common 자격 저장
        RealmAuthAdapter adapter = pickAdapter(AuthRealm.COMMON);
        adapter.registerCredentials(member, req.commonAuth().email(), req.commonAuth().password());

        String keyPath = "admins/" + member.getId() + "/" + signImage.getOriginalFilename();
        String keyName = amazonS3Manager.generateKeyName(keyPath);
        String signUrl = amazonS3Manager.uploadFile(keyName, signImage);
        CommonInfoPayloadDTO info = req.commonInfo();
        var sp = Optional.ofNullable(info.selectedPlace())
                .orElseThrow(() -> new CustomAuthException(ErrorStatus._BAD_REQUEST)); // selectedPlace 필수

        // selectedPlace로부터 주소/좌표 생성
        String address = pickDisplayAddress(sp.getRoadAddress(), sp.getAddress());
        Double lat = sp.getLatitude();
        Double lng = sp.getLongitude();
        Point point = toPoint(lat, lng);

        // 3) Admin 프로필 생성
        Admin admin = adminRepository.save(
                Admin.builder()
                        .major(req.commonAuth().major())
                        .department(req.commonAuth().department())
                        .university(req.commonAuth().university())
                        .member(member)
                        .name(info.name())
                        .officeAddress(address)
                        .detailAddress(info.detailAddress())
                        .signImageUrl(signUrl)
                        .point(point)
                        .latitude(lat)
                        .longitude(lng)
                        .build());

        // 4) 토큰 발급
        TokensDTO tokens = jwtUtil.issueTokens(
                member.getId(),
                req.commonAuth().email(),
                UserRole.ADMIN,
                adapter.authRealmValue());

        // 5) SignUpResponseDTO 생성
        return SignUpResponseDTO.from(member, tokens);
    }

    private EnrollmentStatus parseEnrollmentStatus(String status) {
        if (status == null || status.isBlank()) {
            return EnrollmentStatus.ENROLLED;
        }

        if (status.contains("재학")) {
            return EnrollmentStatus.ENROLLED;
        } else if (status.contains("휴학")) {
            return EnrollmentStatus.LEAVE;
        } else if (status.contains("졸업")) {
            return EnrollmentStatus.GRADUATED;
        } else {
            // 기본값은 재학으로 설정
            return EnrollmentStatus.ENROLLED;
        }
    }

    private Point toPoint(Double lat, Double lng) {
        if (lat == null || lng == null)
            return null;
        Point p = geometryFactory.createPoint(new Coordinate(lng, lat)); // x=lng, y=lat
        p.setSRID(4326);
        return p;
    }

    private String pickDisplayAddress(String road, String jibun) {
        return (road != null && !road.isBlank()) ? road : jibun;
    }
}