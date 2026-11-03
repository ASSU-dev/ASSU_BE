package com.assu.server.domain.auth.security.provider;

import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.security.adapter.RealmAuthAdapter;
import com.assu.server.domain.auth.security.token.LoginUsernamePasswordAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoutingAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

    private final List<RealmAuthAdapter> adapters;

    @Override
    public boolean supports(Class<?> authentication) {
        return LoginUsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private AuthRealm resolveRealm(Authentication auth) {
        if (auth instanceof LoginUsernamePasswordAuthenticationToken) return ((LoginUsernamePasswordAuthenticationToken) auth).getRealm();
        throw new IllegalArgumentException("Unsupported authentication token: " + auth.getClass());
    }

    private RealmAuthAdapter pickAdapter(AuthRealm realm) {
        return adapters.stream()
                .filter(a -> a.supports(realm))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No adapter for realm: " + realm));
    }

    @Override
    protected void additionalAuthenticationChecks(
            UserDetails userDetails,
            UsernamePasswordAuthenticationToken authentication)
            throws AuthenticationException {

        AuthRealm realm = resolveRealm(authentication);
        RealmAuthAdapter adapter = pickAdapter(realm);

        String presented = (authentication.getCredentials() != null)
                ? authentication.getCredentials().toString()
                : null;

        if (presented == null || !adapter.passwordEncoder().matches(presented, userDetails.getPassword())) {
            log.debug("[AUTH_CHAIN] stage=provider-credentials realm={} result=mismatch", realm);
            throw new BadCredentialsException("Bad credentials");
        }
        log.debug("[AUTH_CHAIN] stage=provider-credentials realm={} result=matched", realm);
    }

    @Override
    protected UserDetails retrieveUser(
            String username,
            UsernamePasswordAuthenticationToken authentication)
            throws AuthenticationException {

        AuthRealm realm = resolveRealm(authentication);
        RealmAuthAdapter adapter = pickAdapter(realm);
        log.debug("[AUTH_CHAIN] stage=provider-route realm={} adapter={}", realm, adapter.getClass().getSimpleName());
        return adapter.loadUserDetails(username);
    }
}

