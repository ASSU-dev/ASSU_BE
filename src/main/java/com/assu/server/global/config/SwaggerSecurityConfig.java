package com.assu.server.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("prod")
public class SwaggerSecurityConfig {

    private static final String[] SWAGGER_PATHS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/swagger-resources/**"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            @Value("${swagger.auth.username}") String username,
            @Value("${swagger.auth.password}") String password
    ) throws Exception {
        http
                .securityMatcher(SWAGGER_PATHS)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .authenticationManager(swaggerAuthenticationManager(passwordEncoder, username, password))
                .httpBasic(basic -> basic.realmName("ASSU Swagger"))
                .formLogin(form -> form.disable());

        return http.build();
    }

    // 회원 인증용 전역 AuthenticationManager로 위임되지 않도록 Swagger 전용 매니저를 분리
    private AuthenticationManager swaggerAuthenticationManager(
            PasswordEncoder passwordEncoder, String username, String password
    ) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("swagger.auth.username / swagger.auth.password must be set in prod");
        }
        InMemoryUserDetailsManager userDetailsService = new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(passwordEncoder.encode(password))
                        .roles("SWAGGER")
                        .build()
        );
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
