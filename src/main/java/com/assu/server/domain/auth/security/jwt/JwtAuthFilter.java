package com.assu.server.domain.auth.security.jwt;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증 필터.
 *
 * 책임:
 * - 보호 자원에 대해 Authorization 헤더의 Bearer 토큰을 검증하고 SecurityContext에
 * Authentication을 설정한다.
 * - 토큰 재발급 엔드포인트(/auth/token/reissue)에서는
 * 1) Access 토큰(만료 허용)의 서명을 검증하고 블랙리스트 여부를 확인,
 * 2) Refresh 토큰의 서명/만료를 검증하고 Redis 저장 여부를 확인한 뒤,
 * 3) 만료된 Access 토큰에서 Authentication을 복원해 컨텍스트에 주입한다.
 *
 * 주의:
 * - 화이트리스트는 Swagger 등 공개 리소스에 한정한다. /auth/** 전체를 우회시키지 않는다.
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${jwt.header}")
    private String jwtHeader;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    private static final AntPathMatcher PATH = new AntPathMatcher();
    // 공개 경로(필터 우회)
    private static final String[] WHITELIST = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/swagger-resources/**", "/webjars/**",
            // Auth (로그아웃/탈퇴/리프레시 제외)
            "/auth/phone-verification/check-and-send",
            "/auth/phone-verification/verify",
            "/auth/email-verification/check",
            "/auth/students/signup",
            "/auth/partners/signup",
            "/auth/admins/signup",
            "/auth/commons/login",
            "/auth/students/login",
            "/auth/tokens/refresh",
            "/auth/students/ssu-verify"
    };

    /**
     * 이 요청에 대해 필터를 적용하지 않을지 여부를 판단하는 함수
     * 화이트리스트 패턴은 우회
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()))
            return true; // CORS preflight 우회
        if (PATH.match("/auth/tokens/refresh", uri))
            return false; // 토큰 재발급은 필터 적용
        for (String p : WHITELIST)
            if (PATH.match(p, uri))
                return true; // 나머지 공개 경로 우회
        return false; // 보호 자원은 필터 적용
    }

    /**
     * Authorization 헤더가 존재하고 Bearer 포맷인지 확인한다.
     *
     * @throws CustomAuthException 헤더 누락/형식 오류
     */
    private static void requireBearerAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null) {
            throw new CustomAuthException(ErrorStatus.JWT_TOKEN_NOT_RECEIVED);
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new CustomAuthException(ErrorStatus.JWT_TOKEN_OUT_OF_FORM);
        }
    }

    /**
     * 실제 필터링 로직.
     * - 재발급 경로: Access(서명만), Refresh 검증 + 블랙리스트/Redis 확인 후 Authentication 세팅
     * - 일반 보호 경로: Access 검증 + 블랙리스트 확인 후 Authentication 세팅
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader(jwtHeader);
        String requestUri = request.getRequestURI();

        // ───────── 재발급 경로 처리 (/auth/token/reissue) ─────────
        if (PATH.match("/auth/tokens/refresh", requestUri)) {
            String refreshToken = request.getHeader("refreshToken");
            try {
                // Bearer 헤더 검증
                requireBearerAuthorizationHeader(authorizationHeader);
                if (refreshToken == null) {
                    throw new CustomAuthException(ErrorStatus.JWT_TOKEN_NOT_RECEIVED);
                }

                // Access 토큰: 서명만 검증(만료 허용) 및 블랙리스트 확인(JTI)
                String accessToken = jwtUtil.getTokenFromHeader(authorizationHeader);
                Claims accessClaims = jwtUtil.validateTokenOnlySignature(accessToken);
                String accessJti = accessClaims.getId();
                Boolean accessBlacklisted = redisTemplate.hasKey("blacklist:" + accessJti);
                if (Boolean.TRUE.equals(accessBlacklisted)) {
                    throw new CustomAuthException(ErrorStatus.LOGOUT_USER);
                }

                // Refresh 토큰: 서명/만료 검증 + Redis 저장 여부 확인
                jwtUtil.validateRefreshToken(refreshToken);
                Claims refreshClaims = jwtUtil.validateTokenOnlySignature(refreshToken); // 만료 전이어야 함
                Long memberIdFromRefresh = ((Number) refreshClaims.get("userId")).longValue();
                String refreshJti = refreshClaims.getId();
                String refreshKey = String.format("refresh:%d:%s", memberIdFromRefresh, refreshJti);
                Boolean refreshExists = redisTemplate.hasKey(refreshKey);
                if (Boolean.FALSE.equals(refreshExists)) {
                    // 저장된 RT가 없으면 유효하지 않은 재발급 시도
                    throw new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION);
                }

                // 컨텍스트에 만료된 Access 토큰으로부터 Authentication 복원
                Authentication authentication = jwtUtil.getAuthenticationFromExpiredAccessToken(accessToken);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                chain.doFilter(request, response);
                return;
            } catch (Exception exception) {
                log.error("인증 과정 중, 예상치 못한 예외 발생: {}", exception.getMessage(), exception);
                throw new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION);
            }
        }

        // ───────── 일반 보호 자원 처리 ─────────
        // Authorization 헤더가 없거나 Bearer 형식이 아니면 그대로 통과(익명으로 처리됨)
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String accessToken = jwtUtil.getTokenFromHeader(authorizationHeader);

            // 블랙리스트 확인(만료 허용 X) + Authentication 복원
            jwtUtil.assertNotBlacklisted(accessToken);
            Authentication authentication = jwtUtil.getAuthentication(accessToken);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (Exception exception) {
            log.error("인증 과정 중, 예상치 못한 예외 발생: {}", exception.getMessage(), exception);
            throw new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION);
        }
    }
}