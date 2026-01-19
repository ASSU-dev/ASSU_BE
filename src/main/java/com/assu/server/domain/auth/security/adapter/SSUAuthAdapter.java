package com.assu.server.domain.auth.security.adapter;

import com.assu.server.domain.auth.entity.AuthRealm;
import com.assu.server.domain.auth.entity.SSUAuth;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.SSUAuthRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SSUAuthAdapter implements RealmAuthAdapter {

    private final SSUAuthRepository ssuAuthRepository;
    private final MemberRepository memberRepository;

    @Override
    public boolean supports(AuthRealm realm) {
        return realm == AuthRealm.SSU;
    }

    @Override
    public UserDetails loadUserDetails(String studentNumber) {
        SSUAuth sa = ssuAuthRepository.findByStudentNumber(studentNumber)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
        var m = sa.getMember();
        boolean enabled = m.getIsActivated() == ActivationStatus.ACTIVE;
        String authority = "ROLE_" + m.getRole().name();

        // sToken/sIdno 기반 인증이므로 더미 패스워드 사용
        return org.springframework.security.core.userdetails.User
                .withUsername(sa.getStudentNumber())
                .password("") // 더미 패스워드 (실제 인증은 sToken/sIdno로 수행)
                .authorities(authority)
                .accountExpired(false).accountLocked(false).credentialsExpired(false)
                .disabled(!enabled)
                .build();
    }

    @Override
    public Member loadMember(String studentNumber) {
        Member member = ssuAuthRepository.findByStudentNumber(studentNumber)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER))
                .getMember();

        SSUAuth ssuAuth = member.getSsuAuth();
        ssuAuth.setAuthenticatedAt(LocalDateTime.now());
        ssuAuthRepository.save(member.getSsuAuth());

        if (member.getDeletedAt() != null) {
            member.setDeletedAt(null);
            memberRepository.save(member);
        }

        return member;
    }

    @Override
    public void registerCredentials(Member member, String studentNumber, String rawPassword) {
        if (ssuAuthRepository.existsByStudentNumber(studentNumber)) {
            throw new CustomAuthException(ErrorStatus.EXISTED_STUDENT);
        }
        ssuAuthRepository.save(
                SSUAuth.builder()
                        .member(member)
                        .studentNumber(studentNumber)
                        .isAuthenticated(true)
                        .authenticatedAt(LocalDateTime.now())
                        .build());
    }

    @Override
    public PasswordEncoder passwordEncoder() {
        // 더미 패스워드 인코더 (실제 인증은 sToken/sIdno로 수행)
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return true; // 항상 true 반환 (실제 인증은 sToken/sIdno로 수행)
            }
        };
    }

    @Override
    public String authRealmValue() {
        return "SSU";
    }
}