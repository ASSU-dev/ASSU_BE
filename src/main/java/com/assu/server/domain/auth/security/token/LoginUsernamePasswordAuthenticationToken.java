package com.assu.server.domain.auth.security.token;

import com.assu.server.domain.auth.entity.enums.AuthRealm;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

// 단일 인증 토큰
public class LoginUsernamePasswordAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private final AuthRealm realm;

    public LoginUsernamePasswordAuthenticationToken(AuthRealm realm, String principal, String credentials) {
        super(principal, credentials);
        this.realm = realm;
    }

    public AuthRealm getRealm() { return realm; }
}
