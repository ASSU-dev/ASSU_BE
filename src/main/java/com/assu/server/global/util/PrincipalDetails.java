package com.assu.server.global.util;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor
public class PrincipalDetails implements UserDetails {

    private final Long memberId;                     // 항상 존재
    private final String username;                   // email 또는 studentNumber
    private final String password;                   // DaoAuthenticationProvider 단계에서만 사용
    private final UserRole role;                     // STUDENT/PARTNER/ADMIN
    private final AuthRealm authRealm;               // COMMON or SSU or 추후 확장..
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    private final Member member;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 단일 롤 → ROLE_ 접두사 필수
        String roleName = "ROLE_" + member.getRole().name(); // STUDENT → ROLE_STUDENT
        return List.of(new SimpleGrantedAuthority(roleName));
    }

    @Override
    public String getPassword() {
        // 폼 로그인/DaoAuthenticationProvider를 쓴다면 반드시 반환
        return member.getCommonAuth().getHashedPassword();
    }

    @Override
    public String getUsername() {
        // 인증 후 Authentication.getName() 으로 쓰일 값
        return member.getId().toString();
    }

    public Long getId() {
        // 인증 후 Authentication.getId() 으로 쓰일 값
        return member.getId();
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return member.getIsActivated().equals(ActivationStatus.ACTIVE); // 사용자가 Disabled면 DisabledException 방지/반영
    }

}