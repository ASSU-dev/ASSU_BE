package com.assu.server.domain.auth.security.adapter;

import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.entity.CommonAuth;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
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
public class CommonAuthAdapter implements RealmAuthAdapter {

    private final CommonAuthRepository commonAuthRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean supports(AuthRealm realm) {
        return realm == AuthRealm.COMMON;
    }

    @Override
    public UserDetails loadUserDetails(String email) {
        CommonAuth ca = commonAuthRepository.findByEmail(email)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
        var m = ca.getMember();
        boolean enabled = m.getIsActivated() == ActivationStatus.ACTIVE;
        String authority = "ROLE_" + m.getRole().name();

        return org.springframework.security.core.userdetails.User
                .withUsername(ca.getEmail())
                .password(ca.getHashedPassword()) // BCrypt 해시
                .authorities(authority)
                .accountExpired(false).accountLocked(false).credentialsExpired(false)
                .disabled(!enabled)
                .build();
    }

    @Override
    public Member loadMember(String email) {
        Member member = commonAuthRepository.findByEmail(email)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER))
                .getMember();

        CommonAuth commonAuth = member.getCommonAuth();
        commonAuth.setLastLoginAt(LocalDateTime.now());
        commonAuthRepository.save(commonAuth);

        if (member.getDeletedAt() != null) {
            member.setDeletedAt(null);
            memberRepository.save(member);
        }

        return member;
    }

    @Override
    public void registerCredentials(Member member, String email, String rawPassword) {
        if (commonAuthRepository.existsByEmail(email)) {
            throw new CustomAuthException(ErrorStatus.EXISTED_EMAIL);
        }
        String hash = passwordEncoder.encode(rawPassword);
        commonAuthRepository.save(
                CommonAuth.builder()
                        .member(member)
                        .email(email)
                        .hashedPassword(hash)
                        .lastLoginAt(LocalDateTime.now())
                        .build()
        );
    }

    @Override
    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    @Override
    public String authRealmValue() {
        return "COMMON";
    }
}
