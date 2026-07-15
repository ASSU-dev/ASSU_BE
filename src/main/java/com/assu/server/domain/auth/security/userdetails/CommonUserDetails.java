package com.assu.server.domain.auth.security.userdetails;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 공통(이메일/비밀번호) 로그인용 UserDetails.
 * 인증 이후 추가 조회 없이 SUSPEND/역할 검사를 할 수 있도록 status와 role을 함께 담는다.
 */
@Getter
public class CommonUserDetails extends User {

    private final ActivationStatus status;
    private final UserRole role;

    public CommonUserDetails(
            String username,
            String password,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities,
            ActivationStatus status,
            UserRole role
    ) {
        super(username, password, enabled, true, true, true, authorities);
        this.status = status;
        this.role = role;
    }
}
