package com.assu.server.global.config;

import com.assu.server.domain.auth.security.jwt.JwtAuthFilter;
import com.assu.server.global.security.RestAccessDeniedHandler;
import com.assu.server.global.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/verify").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/ws/**","/ws").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/swagger-resources/**", "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(
                                "/auth/phone-verification/check-and-send",
                                "/auth/phone-verification/verify",
                                "/auth/email-verification/check",
                                "/auth/students/signup",
                                "/auth/partners/signup",
                                "/auth/admins/signup",
                                "/auth/commons/login",
                                "/auth/backoffice/login",
                                "/auth/students/login",
                                "/auth/tokens/refresh",
                                "/auth/backoffice/tokens/refresh",
                                "/auth/students/ssu-verify"
                        ).permitAll()
                        .requestMatchers("/backoffice/**").hasRole("BACKOFFICE")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/partner/**").hasRole("PARTNER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers("/actuator/**");
    }
}
