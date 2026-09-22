package com.assu.server.domain.auth.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.auth.dto.signup.*;
import com.assu.server.domain.auth.dto.signup.common.CommonInfoPayloadDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;
import com.assu.server.domain.auth.entity.CommonAuth;
import com.assu.server.domain.auth.entity.SSUAuth;
import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.domain.auth.repository.SSUAuthRepository;
import com.assu.server.domain.auth.security.adapter.RealmAuthAdapter;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.common.entity.enums.EnrollmentStatus;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.domain.student.service.StudentService;
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
    private final StudentService studentService;
    private final PhoneAuthService phoneAuthService;
    private final CommonAuthRepository commonAuthRepository;

    private RealmAuthAdapter pickAdapter(AuthRealm realm) {
        return realmAuthAdapters.stream()
                .filter(a -> a.supports(realm))
                .findFirst()
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION));
    }

    @Override
    public SignUpResponseDTO signupSsuStudent(StudentTokenSignUpRequestDTO req) {

        // 1) 유세인트 인증 및 학생 정보 추출
        USaintAuthRequestDTO authRequest = new USaintAuthRequestDTO(
                req.studentTokenAuth().sToken(),
                req.studentTokenAuth().sIdno()
        );

        USaintAuthResponseDTO authResponse = ssuAuthService.uSaintAuth(authRequest);

        // 2) 기존 계정 확인 — 탈퇴 유예기간 내라면 신규 생성 대신 복구한다
        Optional<SSUAuth> existingAuth = ssuAuthRepository.findByStudentNumber(authResponse.studentNumber());
        if (existingAuth.isPresent()) {
            Member existingMember = existingAuth.get().getMember();
            if (!existingMember.isWithdrawn()) {
                throw new CustomAuthException(ErrorStatus.EXISTED_STUDENT);
            }
            return restoreWithdrawnStudent(existingMember, req, authResponse);
        }

        // 3) member 생성
        Member member = memberRepository.save(
                Member.builder()
                        .isLocationTermAgreed(req.locationAgree())
                        .isMarketingTermAgreed(req.marketingAgree())
                        .role(UserRole.STUDENT)
                        .isActivated(ActivationStatus.ACTIVE)
                        .build());

        // 4) SSUAuth 생성 (학번만 저장)
        RealmAuthAdapter adapter = pickAdapter(AuthRealm.SSU);
        adapter.registerCredentials(member, authResponse.studentNumber(), ""); // 더미 패스워드

        // 5) Student 프로필 생성 (크롤링된 정보 사용)
        Major major = Major.fromDisplayName(authResponse.majorStr());

        Student student = studentRepository.save(Student.builder()
                .member(member)
                .name(authResponse.name())
                .department(major.getDepartment())
                .major(major)
                .enrollmentStatus(parseEnrollmentStatus(authResponse.enrollmentStatus()))
                .yearSemester(authResponse.yearSemester())
                .university(University.SSU) // Todo: 추후 다른 대학도 추가할 시 로직 변경 필요
                .stamp(0)
                .build());
        member.setProfile(student);

        // 6) 가입 시점 사용 가능 제휴 동기화 (자정 배치와 별개로 즉시 반영)
        studentService.syncUserPapersForStudent(student.getId());

        // 7) JWT 토큰 발급
        TokensDTO tokens = jwtUtil.issueTokens(
                member.getId(),
                authResponse.studentNumber(),
                UserRole.STUDENT,
                "SSU");

        return SignUpResponseDTO.from(member, tokens);
    }

    private SignUpResponseDTO restoreWithdrawnStudent(
            Member member,
            StudentTokenSignUpRequestDTO req,
            USaintAuthResponseDTO authResponse
    ) {
        Student student = member.getStudentProfile();
        if (student == null) {
            throw new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER);
        }

        member.restore();
        member.updateTermAgreements(req.locationAgree(), req.marketingAgree());
        memberRepository.save(member);

        Major major = Major.fromDisplayName(authResponse.majorStr());
        student.updateStudentInfo(
                authResponse.name(),
                major,
                major.getDepartment(),
                parseEnrollmentStatus(authResponse.enrollmentStatus()),
                authResponse.yearSemester()
        );
        studentRepository.save(student);

        // 탈퇴 기간 중 변동된 제휴를 반영한다
        studentService.syncUserPapersForStudent(student.getId());

        TokensDTO tokens = jwtUtil.issueTokens(
                member.getId(),
                authResponse.studentNumber(),
                UserRole.STUDENT,
                "SSU");

        return SignUpResponseDTO.from(member, tokens);
    }

    @Override
    public SignUpResponseDTO signupPartner(PartnerSignUpRequestDTO req, MultipartFile licenseImage) {
        phoneAuthService.consumeVerification(req.phoneNumber());

        if (!phoneAuthService.isMasterPhoneNumber(req.phoneNumber())
                && (partnerRepository.existsByPhoneNumAndMember_DeletedAtIsNull(req.phoneNumber())
                        || adminRepository.existsByPhoneNumAndMember_DeletedAtIsNull(req.phoneNumber()))) {
            throw new CustomAuthException(ErrorStatus.EXISTED_PHONE);
        }

        CommonInfoPayloadDTO info = req.commonInfo();
        var sp = Optional.ofNullable(info.selectedPlace())
                .orElseThrow(() -> new CustomAuthException(ErrorStatus._BAD_REQUEST)); // selectedPlace 필수

        String address = pickDisplayAddress(sp.getRoadAddress(), sp.getAddress());
        Double lat = sp.getLatitude();
        Double lng = sp.getLongitude();
        Point point = toPoint(lat, lng);

        // 1) 기존 계정 확인 — 탈퇴 유예기간 내라면 신규 생성 대신 복구한다
        Member withdrawnMember = findRestorableMember(req.commonAuth().email(), UserRole.PARTNER);
        if (withdrawnMember != null) {
            return restoreWithdrawnPartner(withdrawnMember, req, licenseImage, address, lat, lng, point);
        }

        // 2) member 생성 (마스터 번호는 iOS 심사 대응을 위해 승인 절차 없이 즉시 ACTIVE)
        Member member = memberRepository.save(
                Member.builder()
                        .isLocationTermAgreed(req.locationAgree())
                        .isMarketingTermAgreed(req.marketingAgree())
                        .role(UserRole.PARTNER)
                        .isActivated(resolveInitialActivationStatus(req.phoneNumber()))
                        .build());

        // 3) RealmAuthAdapter 로 Common 자격 저장
        RealmAuthAdapter adapter = pickAdapter(AuthRealm.COMMON);
        adapter.registerCredentials(member, req.commonAuth().email(), req.commonAuth().password());

        String licenseUrl = uploadPartnerLicense(member.getId(), licenseImage);

        // 4) Partner 프로필 생성
        Partner partner = partnerRepository.save(
                Partner.builder()
                        .member(member)
                        .name(info.name())
                        .phoneNum(req.phoneNumber())
                        .isPhoneVerified(true)
                        .address(address)
                        .detailAddress(info.detailAddress())
                        .licenseUrl(licenseUrl)
                        .point(point)
                        .latitude(lat)
                        .longitude(lng)
                        .build());
        member.setProfile(partner);

        linkStore(partner, info.name(), address, info.detailAddress(), lat, lng, point);

        return SignUpResponseDTO.from(member, null);
    }

    /**
     * 이메일로 복구 가능한 탈퇴 회원을 조회한다.
     *
     * @return 복구 대상 회원. 가입 이력이 없으면 null
     * @throws CustomAuthException 활성 회원이거나 역할이 다른 경우
     */
    private Member findRestorableMember(String email, UserRole role) {
        CommonAuth commonAuth = commonAuthRepository.findByEmail(email).orElse(null);
        if (commonAuth == null) {
            return null;
        }

        Member member = commonAuth.getMember();
        if (!member.isWithdrawn() || member.getRole() != role) {
            throw new CustomAuthException(ErrorStatus.EXISTED_EMAIL);
        }
        return member;
    }

    private SignUpResponseDTO restoreWithdrawnPartner(
            Member member,
            PartnerSignUpRequestDTO req,
            MultipartFile licenseImage,
            String address,
            Double lat,
            Double lng,
            Point point
    ) {
        Partner partner = member.getPartnerProfile();
        if (partner == null) {
            throw new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER);
        }

        // isActivated는 유지한다 — 승인받았던 업체는 재승인 없이 바로 로그인 가능
        member.restore();
        member.updateTermAgreements(req.locationAgree(), req.marketingAgree());
        memberRepository.save(member);

        RealmAuthAdapter adapter = pickAdapter(AuthRealm.COMMON);
        member.getCommonAuth()
                .updatePassword(adapter.passwordEncoder().encode(req.commonAuth().password()));

        CommonInfoPayloadDTO info = req.commonInfo();
        String licenseUrl = uploadPartnerLicense(member.getId(), licenseImage);
        partner.updateBusinessInfo(info.name(), req.phoneNumber(), address, info.detailAddress(),
                licenseUrl, point, lat, lng);
        partnerRepository.save(partner);

        linkStore(partner, info.name(), address, info.detailAddress(), lat, lng, point);

        return SignUpResponseDTO.from(member, null);
    }

    private String uploadPartnerLicense(Long memberId, MultipartFile licenseImage) {
        String keyPath = "partners/" + memberId + "/" + licenseImage.getOriginalFilename();
        String keyName = amazonS3Manager.generateKeyName(keyPath);
        return amazonS3Manager.uploadFile(keyName, licenseImage);
    }

    private void linkStore(Partner partner, String name, String address, String detailAddress,
                           Double lat, Double lng, Point point) {
        Optional<Store> storeOpt = storeRepository.findBySameAddress(address, detailAddress);
        if (storeOpt.isPresent()) {
            Store store = storeOpt.get();
            store.linkPartner(partner);
            store.setName(name);
            store.setGeo(lat, lng, point);
            storeRepository.save(store);
        } else {
            Store newly = Store.builder()
                    .partner(partner)
                    .rate(0)
                    .isActivate(ActivationStatus.SUSPEND)
                    .name(name)
                    .address(address)
                    .detailAddress(detailAddress)
                    .latitude(lat)
                    .longitude(lng)
                    .point(point)
                    .build();
            storeRepository.save(newly);
        }
    }

    @Override
    public List<SignUpResponseDTO> signupBatchPartner(List<PartnerBatchSignUpItemDTO> requests) {
        return requests.stream().map(req -> {
            Member member = memberRepository.save(
                    Member.builder()
                            .isLocationTermAgreed(true)
                            .isMarketingTermAgreed(true)
                            .role(UserRole.PARTNER)
                            .isActivated(ActivationStatus.SUSPEND)
                            .build());

            RealmAuthAdapter adapter = pickAdapter(AuthRealm.COMMON);
            adapter.registerCredentials(member, req.email(), req.password());

            String roadAddress = req.roadAddress() != null ? req.roadAddress() : "";
            Double lat = req.latitude() != null ? req.latitude() : 0.0;
            Double lng = req.longitude() != null ? req.longitude() : 0.0;
            Point point = toPoint(lat, lng);

            Partner partner = partnerRepository.save(
                    Partner.builder()
                            .member(member)
                            .name(req.name())
                            .phoneNum(null)
                            .isPhoneVerified(false)
                            .address(roadAddress)
                            .detailAddress(null)
                            .licenseUrl(null)
                            .point(point)
                            .latitude(lat)
                            .longitude(lng)
                            .build());
            member.setProfile(partner);

            linkStore(partner, req.name(), roadAddress, null, lat, lng, point);

            return SignUpResponseDTO.from(member, null);
        }).toList();
    }

    @Override
    public SignUpResponseDTO signupAdmin(AdminSignUpRequestDTO req, MultipartFile signImage) {
        phoneAuthService.consumeVerification(req.phoneNumber());

        if (!phoneAuthService.isMasterPhoneNumber(req.phoneNumber())
                && (partnerRepository.existsByPhoneNumAndMember_DeletedAtIsNull(req.phoneNumber())
                        || adminRepository.existsByPhoneNumAndMember_DeletedAtIsNull(req.phoneNumber()))) {
            throw new CustomAuthException(ErrorStatus.EXISTED_PHONE);
        }

        Member withdrawnMember = findRestorableMember(req.commonAuth().email(), UserRole.ADMIN);
        if (withdrawnMember != null) {
            return restoreWithdrawnAdmin(withdrawnMember, req, signImage);
        }

        // 1) member 생성 (마스터 번호는 iOS 심사 대응을 위해 승인 절차 없이 즉시 ACTIVE)
        Member member = memberRepository.save(
                Member.builder()
                        .isLocationTermAgreed(req.locationAgree())
                        .isMarketingTermAgreed(req.marketingAgree())
                        .role(UserRole.ADMIN)
                        .isActivated(resolveInitialActivationStatus(req.phoneNumber()))
                        .build());

        // 2) RealmAuthAdapter 로 Common 자격 저장
        RealmAuthAdapter adapter = pickAdapter(AuthRealm.COMMON);
        adapter.registerCredentials(member, req.commonAuth().email(), req.commonAuth().password());

        String signUrl = uploadAdminSign(member.getId(), signImage);
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
                        .phoneNum(req.phoneNumber())
                        .isPhoneVerified(true)
                        .officeAddress(address)
                        .detailAddress(info.detailAddress())
                        .signImageUrl(signUrl)
                        .point(point)
                        .latitude(lat)
                        .longitude(lng)
                        .build());
        member.setProfile(admin);

        return SignUpResponseDTO.from(member, null);
    }

    private SignUpResponseDTO restoreWithdrawnAdmin(
            Member member,
            AdminSignUpRequestDTO req,
            MultipartFile signImage
    ) {
        Admin admin = member.getAdminProfile();
        if (admin == null) {
            throw new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER);
        }

        CommonInfoPayloadDTO info = req.commonInfo();
        var sp = Optional.ofNullable(info.selectedPlace())
                .orElseThrow(() -> new CustomAuthException(ErrorStatus._BAD_REQUEST)); // selectedPlace 필수

        String address = pickDisplayAddress(sp.getRoadAddress(), sp.getAddress());
        Double lat = sp.getLatitude();
        Double lng = sp.getLongitude();
        Point point = toPoint(lat, lng);

        // isActivated는 유지한다 — 승인받았던 학생회는 재승인 없이 바로 로그인 가능
        member.restore();
        member.updateTermAgreements(req.locationAgree(), req.marketingAgree());
        memberRepository.save(member);

        RealmAuthAdapter adapter = pickAdapter(AuthRealm.COMMON);
        member.getCommonAuth()
                .updatePassword(adapter.passwordEncoder().encode(req.commonAuth().password()));

        String signUrl = uploadAdminSign(member.getId(), signImage);
        admin.updateOrganizationInfo(info.name(), req.phoneNumber(), address, info.detailAddress(),
                signUrl, req.commonAuth().major(), req.commonAuth().department(),
                req.commonAuth().university(), point, lat, lng);
        adminRepository.save(admin);

        return SignUpResponseDTO.from(member, null);
    }

    private String uploadAdminSign(Long memberId, MultipartFile signImage) {
        String keyPath = "admins/" + memberId + "/" + signImage.getOriginalFilename();
        String keyName = amazonS3Manager.generateKeyName(keyPath);
        return amazonS3Manager.uploadFile(keyName, signImage);
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

    private ActivationStatus resolveInitialActivationStatus(String phoneNumber) {
        return phoneAuthService.isMasterPhoneNumber(phoneNumber)
                ? ActivationStatus.ACTIVE
                : ActivationStatus.SUSPEND;
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