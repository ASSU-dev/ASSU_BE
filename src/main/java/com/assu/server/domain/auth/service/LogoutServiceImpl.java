package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.security.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutServiceImpl implements LogoutService {

    private final JwtUtil jwtUtil;

    @Override
    public void logout(String authorization) {
        String rawAccessToken = jwtUtil.getTokenFromHeader(authorization);

        // 1) Access 토큰 Claims 추출 (만료 허용, 서명 검증)
        Claims accessClaims = jwtUtil.validateTokenOnlySignature(rawAccessToken);

        // 2) Access 블랙리스트 등록
        jwtUtil.blacklistAccess(rawAccessToken);

        // 3) 해당 사용자(memberId)의 모든 Refresh 토큰 키 제거 (전역 로그아웃)
        Long memberId = ((Number) accessClaims.get("userId")).longValue();
        jwtUtil.removeAllRefreshTokens(memberId);
    }
}
