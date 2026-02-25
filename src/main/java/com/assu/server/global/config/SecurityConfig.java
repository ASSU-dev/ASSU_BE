package com.assu.server.global.config;

import com.assu.server.domain.auth.security.jwt.JwtAuthFilter;
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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {}) // 기본 CORS 구성 사용(필요하면 CorsConfigurationSource 빈 추가)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ✅ WebSocket 핸드셰이크 허용 (네이티브 + SockJS 모두 포함)
                        .requestMatchers("/ws/**","/ws").permitAll()

                        // Swagger 등 공개 리소스
                        .requestMatchers(
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/swagger-resources/**", "/webjars/**"
                        ).permitAll()
                        // 헬스 체크 용
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(// Auth (로그아웃 제외)
                                "/auth/phone-verification/check-and-send",
                                "/auth/phone-verification/verify",
                                "/auth/email-verification/check",
                                "/auth/students/signup",
                                "/auth/partners/signup",
                                "/auth/admins/signup",
                                "/auth/commons/login",
                                "/auth/students/login",
                                "/auth/tokens/refresh",
                                "/auth/students/ssu-verify",
                                "/map/place",
                                "/member/inquiries/{inquiry-id}/answer"
                        ).permitAll()
                    .requestMatchers("/ws/**").permitAll()
                        // 나머지는 인증 필요

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
        		.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }


    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        // /actuator로 시작하는 모든 요청은 보안 필터 체인 자체를 거치지 않게 합니다.
        return (web) -> web.ignoring()
            .requestMatchers("/actuator/**");
    }

}
