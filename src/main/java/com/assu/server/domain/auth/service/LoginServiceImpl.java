package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.dto.login.LoginResponseDTO;
import com.assu.server.domain.auth.dto.login.RefreshResponseDTO;
import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.auth.dto.signup.student.StudentTokenAuthPayloadDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;
import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.domain.auth.security.adapter.RealmAuthAdapter;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.auth.security.token.LoginUsernamePasswordAuthenticationToken;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.user.entity.Student;
import com.assu.server.domain.user.entity.enums.EnrollmentStatus;
import com.assu.server.domain.user.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LoginServiceImpl implements LoginService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final SSUAuthService ssuAuthService;
    private final CommonAuthRepository commonAuthRepository;
    private final StudentRepository studentRepository;

    private final List<RealmAuthAdapter> realmAuthAdapters;

    private RealmAuthAdapter pickAdapter(AuthRealm realm) {
        return realmAuthAdapters.stream()
                .filter(a -> a.supports(realm))
                .findFirst()
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION));
    }

    /**
     * 공통(파트너/관리자) 로그인: 이메일/비밀번호 기반.
     * 1) 인증 성공 시 CommonAuth 조회 및 탈퇴 회원 복구
     * 2) JWT 발급: username=email, authRealm=COMMON
     */
    @Override
    public LoginResponseDTO loginCommon(CommonLoginRequestDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                new LoginUsernamePasswordAuthenticationToken(
                        AuthRealm.COMMON,
                        request.email(),
                        request.password()
                )
        );

        RealmAuthAdapter adapter = pickAdapter(AuthRealm.COMMON);

        Member member = adapter.loadMember(authentication.getName());

        // 토큰 발급 (Access 미저장, Refresh는 Redis 저장)
        TokensDTO tokens = jwtUtil.issueTokens(
                member.getId(),
                authentication.getName(),
                member.getRole(),
                adapter.authRealmValue()
        );

        return LoginResponseDTO.from(member, tokens);
    }

    /**
     * 숭실대 학생 로그인: sToken, sIdno 기반.
     * 1) 유세인트 인증으로 학생 정보 확인
     * 2) 기존 회원 확인 및 탈퇴 회원 복구
     * 3) Student 정보 업데이트 (유세인트에서 크롤링한 최신 정보로)
     * 4) JWT 발급: username=studentNumber, authRealm=SSU
     */
    @Override
    public LoginResponseDTO loginSsuStudent(StudentTokenAuthPayloadDTO request) {
        // 1) 유세인트 인증
        USaintAuthRequestDTO authRequest = new USaintAuthRequestDTO(
                request.sToken(),
                request.sIdno()
        );

        USaintAuthResponseDTO authResponse = ssuAuthService.uSaintAuth(authRequest);

        // 2) 기존 회원 확인
        String realmStr = request.university().toString();
        AuthRealm authRealm = AuthRealm.valueOf(realmStr);
        RealmAuthAdapter adapter = pickAdapter(authRealm);

        Member member = adapter.loadMember(authResponse.studentNumber());

        // 3) Student 정보 업데이트
        Student student = member.getStudentProfile();
        if (student == null) {
            throw new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER);
        }

        student.updateStudentInfo(
                authResponse.name(),
                authResponse.major(),
                parseEnrollmentStatus(authResponse.enrollmentStatus()),
                authResponse.yearSemester()
        );

        studentRepository.save(student);

        // 4) 토큰 발급
        TokensDTO tokens = jwtUtil.issueTokens(
                member.getId(),
                authResponse.studentNumber(),
                member.getRole(),
                adapter.authRealmValue()
        );

        return LoginResponseDTO.from(member, tokens);
    }

    /**
     * Refresh 토큰 재발급(회전).
     * 전제: JwtAuthFilter가 /auth/refresh 에서 Access(만료 허용) 서명 검증 및 컨텍스트 세팅을 이미 수행.
     *
     * 절차:
     * 1) RT 서명/만료 검증(jwtUtil.validateRefreshToken)
     * 2) RT의 Claims 추출(만료 X), memberId/jti/username/role/authRealm 획득
     * 3) Redis 키 "refresh:{memberId}:{jti}" 존재 및 값 일치 확인(도난/중복 재사용 차단)
     * 4) 기존 RT 키 삭제(회전), 새 토큰 발급(issueTokens)
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefreshResponseDTO refresh(String refreshToken) {
        TokensDTO rotated = jwtUtil.rotateRefreshToken(refreshToken);

        Long memberId = ((Number) jwtUtil.validateTokenOnlySignature(rotated.accessToken()).get("userId")).longValue();

        return RefreshResponseDTO.from(memberId, rotated);
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
}