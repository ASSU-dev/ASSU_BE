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

@RequiredArgsConstructor
@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${jwt.header}")
    private String jwtHeader;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    private static final AntPathMatcher PATH = new AntPathMatcher();
    private static final String[] WHITELIST = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/swagger-resources/**", "/webjars/**",
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
            "/auth/students/ssu-verify",
            "/actuator/**",
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()))
            return true;
        if (PATH.match("/auth/tokens/refresh", uri) || PATH.match("/auth/backoffice/tokens/refresh", uri))
            return false;
        for (String p : WHITELIST)
            if (PATH.match(p, uri))
                return true;
        return false;
    }

    private static void requireBearerAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null) {
            throw new CustomAuthException(ErrorStatus.JWT_TOKEN_NOT_RECEIVED);
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new CustomAuthException(ErrorStatus.JWT_TOKEN_OUT_OF_FORM);
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader(jwtHeader);
        String requestUri = request.getRequestURI();

        if (PATH.match("/auth/tokens/refresh", requestUri)
                || PATH.match("/auth/backoffice/tokens/refresh", requestUri)) {
            handleRefresh(request, response, chain, authorizationHeader, requestUri);
            return;
        }

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String accessToken = jwtUtil.getTokenFromHeader(authorizationHeader);
            jwtUtil.assertNotBlacklisted(accessToken);
            Claims claims = jwtUtil.validateToken(accessToken);
            assertAudienceForRequest(requestUri, claims);

            Authentication authentication = jwtUtil.getAuthentication(accessToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (CustomAuthException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("인증 과정 중, 예상치 못한 예외 발생: {}", exception.getMessage(), exception);
            throw new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION);
        }
    }

    private void handleRefresh(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            String authorizationHeader,
            String requestUri
    ) throws ServletException, IOException {
        String refreshToken = request.getHeader("RefreshToken");
        try {
            requireBearerAuthorizationHeader(authorizationHeader);
            if (refreshToken == null) {
                throw new CustomAuthException(ErrorStatus.JWT_TOKEN_NOT_RECEIVED);
            }

            String accessToken = jwtUtil.getTokenFromHeader(authorizationHeader);
            Claims accessClaims = jwtUtil.validateTokenOnlySignature(accessToken);
            String accessJti = accessClaims.getId();
            Boolean accessBlacklisted = redisTemplate.hasKey("blacklist:" + accessJti);
            if (Boolean.TRUE.equals(accessBlacklisted)) {
                throw new CustomAuthException(ErrorStatus.LOGOUT_USER);
            }

            jwtUtil.validateRefreshToken(refreshToken);
            Claims refreshClaims = jwtUtil.validateTokenOnlySignature(refreshToken);
            Long memberIdFromRefresh = ((Number) refreshClaims.get("userId")).longValue();
            String refreshJti = refreshClaims.getId();
            String refreshKey = String.format("refresh:%d:%s", memberIdFromRefresh, refreshJti);
            Boolean refreshExists = redisTemplate.hasKey(refreshKey);
            if (Boolean.FALSE.equals(refreshExists)) {
                throw new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION);
            }

            boolean backofficeRefresh = PATH.match("/auth/backoffice/tokens/refresh", requestUri);
            if (backofficeRefresh) {
                jwtUtil.assertAudience(refreshClaims, JwtUtil.AUD_BACKOFFICE);
                jwtUtil.assertAudience(accessClaims, JwtUtil.AUD_BACKOFFICE);
            } else {
                jwtUtil.assertAudience(refreshClaims, JwtUtil.AUD_APP);
                jwtUtil.assertAudience(accessClaims, JwtUtil.AUD_APP);
            }

            Authentication authentication = jwtUtil.getAuthenticationFromExpiredAccessToken(accessToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (CustomAuthException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("인증 과정 중, 예상치 못한 예외 발생: {}", exception.getMessage(), exception);
            throw new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION);
        }
    }

    private void assertAudienceForRequest(String requestUri, Claims claims) {
        if (PATH.match("/backoffice/**", requestUri) || PATH.match("/auth/backoffice/**", requestUri)) {
            jwtUtil.assertAudience(claims, JwtUtil.AUD_BACKOFFICE);
            return;
        }
        jwtUtil.assertAudience(claims, JwtUtil.AUD_APP);
    }
}
