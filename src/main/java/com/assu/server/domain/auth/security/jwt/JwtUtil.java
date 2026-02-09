package com.assu.server.domain.auth.security.jwt;

import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JWT 발급/검증 및 Authentication 복원 유틸리티.
 */
@Component
@RequiredArgsConstructor
@Profile("!test")
public class JwtUtil {

    @Value("${jwt.secret}")
    public String secretKey;

    @Value("${jwt.access-valid-seconds:3600}")      // 1시간 기본
    private int accessValidSeconds;

    @Value("${jwt.refresh-valid-seconds:1209600}")  // 14일 기본
    private int refreshValidSeconds;

    private final MemberRepository memberRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @PostConstruct
    public void clearRedisOnStartup() {
        if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        }
    }

    // ───────── 토큰 생성 공통 유틸 ─────────
    /**
     * 서명용 SecretKey 생성.
     */
    private SecretKey key() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * "Bearer xxx" 헤더에서 실제 토큰 문자열만 추출.
     * @param authorizationHeader Authorization 헤더 값
     * @return 토큰 문자열
     */
    public String getTokenFromHeader(String authorizationHeader) {
        return authorizationHeader.split(" ")[1];
    }

    /**
     * JWT 문자열 생성.
     * @param claims       토큰 클레임
     * @param validSeconds 유효 기간(초)
     * @param jti          JWT ID(고유 식별자)
     * @return 서명된 토큰 문자열
     */
    private String generateToken(Map<String, Object> claims, int validSeconds, String jti) {
        return Jwts.builder()
                .setHeader(Map.of("typ", "JWT"))
                .setClaims(claims)
                .setId(jti)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(ZonedDateTime.now().plusSeconds(validSeconds).toInstant()))
                .signWith(key())
                .compact();
    }

    // ───────── 발급 & 저장 ─────────
    /**
     * Access/Refresh 토큰 발급.
     * - Access: 반환만, 저장하지 않음.
     * - Refresh: Redis에 "refresh:{memberId}:{jti}" 키로 저장(TTL=만료).
     * @param memberId 사용자 ID
     * @param username 이메일 혹은 학번
     * @param role     사용자 역할
     * @param authRealm COMMON / SSU
     * @return 발급된 토큰 세트
     */
    public TokensDTO issueTokens(Long memberId, String username, UserRole role, String authRealm) {
        Map<String, Object> baseClaims = new HashMap<>();
        baseClaims.put("userId", memberId);
        baseClaims.put("username", username);
        baseClaims.put("role", role.name());
        baseClaims.put("authRealm", authRealm);

        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String accessToken = generateToken(baseClaims, accessValidSeconds, accessJti);
        String refreshToken = generateToken(baseClaims, refreshValidSeconds, refreshJti);

        String refreshKey = String.format("refresh:%d:%s", memberId, refreshJti);
        redisTemplate.opsForValue().set(refreshKey, refreshToken, refreshValidSeconds, TimeUnit.SECONDS);

        return TokensDTO.of(accessToken, refreshToken);
    }

    // ───────── 검증 ─────────
    /**
     * Access 토큰 서명/만료 검증.
     * @param token Access 토큰
     * @return 유효한 Claims
     * @throws CustomAuthException 만료/서명 오류
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException exception) {
            throw new CustomAuthException(ErrorStatus.JWT_ACCESS_TOKEN_EXPIRED);
        } catch (Exception exception) {
            throw new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION);
        }
    }

    /**
     * Access 토큰의 서명만 검증(만료 허용)하여 Claims 추출.
     * - 재발급 시 사용.
     * @param token Access 토큰
     * @return Claims(만료된 토큰도 반환)
     * @throws CustomAuthException 서명 오류
     */
    public Claims validateTokenOnlySignature(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException exception) {
            return exception.getClaims(); // 만료되어도 Claims는 사용
        } catch (Exception exception) {
            throw new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION);
        }
    }

    /**
     * Refresh 토큰 서명/만료 검증.
     * - Redis 저장값과의 매칭은 호출부 정책에 따라 별도로 수행 가능.
     * @param refreshToken Refresh 토큰
     * @throws CustomAuthException 만료/서명 오류
     */
    public void validateRefreshToken(String refreshToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(refreshToken).getBody();
        } catch (ExpiredJwtException exception) {
            throw new CustomAuthException(ErrorStatus.JWT_REFRESH_TOKEN_EXPIRED);
        } catch (Exception exception) {
            throw new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION);
        }
    }

    // ───────── Authentication 복원 ─────────
    /**
     * 유효한 Access 토큰을 Authentication(CustomPrincipal + 권한)으로 복원.
     * @param accessToken Access 토큰
     * @return 인증 객체
     */
    public Authentication getAuthentication(String accessToken) {
        Claims claims = validateToken(accessToken); // 만료/서명 체크
        Long memberId = ((Number) claims.get("userId")).longValue();
        String roleName = (String) claims.get("role");
        String authRealmName = (String) claims.get("authRealm");

        // DB 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));

        // PrincipalDetails 빌드
        PrincipalDetails principal = PrincipalDetails.builder()
                .memberId(member.getId())
                .username(member.getId().toString())
                .role(UserRole.valueOf(roleName))
                .authRealm(AuthRealm.valueOf(authRealmName))
                .member(member)
                .enabled(member.getIsActivated().equals(ActivationStatus.ACTIVE))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + roleName)))
                .build();

        // UsernamePasswordAuthenticationToken 에 PrincipalDetails 세팅
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
        );
    }

    /**
     * 만료된 Access 토큰(서명 유효)을 Authentication으로 복원.
     * - 재발급 시 SecurityContext 세팅용.
     * @param expiredAccessToken 만료된 Access 토큰
     * @return 인증 객체
     */
    public Authentication getAuthenticationFromExpiredAccessToken(String expiredAccessToken) {
        Claims claims = validateTokenOnlySignature(expiredAccessToken);

        Long userId = ((Number) claims.get("userId")).longValue();
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_MEMBER));

        UserRole role = UserRole.valueOf((String) claims.get("role"));
        String authRealmString = (String) claims.get("authRealm");

        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

        String username="";
        String password="";

        AuthRealm realm = AuthRealm.valueOf(authRealmString);
        if (realm == AuthRealm.COMMON) {
            username = member.getCommonAuth().getEmail();
            password = member.getCommonAuth().getHashedPassword();
        } else if (realm == AuthRealm.SSU){
            username = member.getSsuAuth().getStudentNumber();
            password = ""; // 더미 처리
        }

        // DB에서 조회한 member를 직접 넣어줌
        PrincipalDetails principal = PrincipalDetails.builder()
                .member(member)
                .memberId(member.getId())
                .username(username)
                .password(password)
                .enabled(member.getIsActivated().equals(ActivationStatus.ACTIVE))
                .role(role)
                .authRealm(realm)
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    // ───────────────────────── 블랙리스트(JTI) ─────────────────────────

    /**
     * Access 토큰이 블랙리스트에 포함되어 있지 않은지 확인.
     * @param accessToken Access 토큰
     * @throws CustomAuthException 블랙리스트에 포함된 경우
     */
    public void assertNotBlacklisted(String accessToken) {
        Claims claims = validateTokenOnlySignature(accessToken);
        String jti = claims.getId();
        Boolean exists = redisTemplate.hasKey("blacklist:" + jti);
        if (Boolean.TRUE.equals(exists)) {
            throw new CustomAuthException(ErrorStatus.LOGOUT_USER);
        }
    }

    /**
     * Access 토큰을 블랙리스트에 추가(남은 만료 시간만큼 TTL 부여).
     * @param accessToken Access 토큰
     */
    public void blacklistAccess(String accessToken) {
        Claims claims = validateTokenOnlySignature(accessToken);
        String jti = claims.getId();
        long ttlSeconds = Math.max(1, (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000);
        redisTemplate.opsForValue().set("blacklist:" + jti, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    // ───────────────────────── Refresh Token ─────────────────────────

    /**
     * 특정 회원의 모든 Refresh 토큰을 Redis에서 제거 (전역 로그아웃용).
     * @param memberId 사용자 ID
     */
    public void removeAllRefreshTokens(Long memberId) {
        String pattern = String.format("refresh:%d:*", memberId);
        Set<String> refreshKeys = redisTemplate.keys(pattern);
        if (refreshKeys != null && !refreshKeys.isEmpty()) {
            redisTemplate.delete(refreshKeys);
        }
    }

    /**
     * Refresh 토큰 유효성 확인 및 회전.
     * - 저장된 RT와 일치 여부 확인
     * - 기존 RT 삭제 후 새 토큰 세트 발급
     */
    public TokensDTO rotateRefreshToken(String refreshToken) {
        // 1) Refresh 토큰 서명/만료 검증
        validateRefreshToken(refreshToken);

        // 2) Claims 추출
        Claims refreshClaims = validateTokenOnlySignature(refreshToken);
        Long memberId = ((Number) refreshClaims.get("userId")).longValue();
        String username = (String) refreshClaims.get("username");
        String roleString = (String) refreshClaims.get("role");
        String authRealm = (String) refreshClaims.get("authRealm");
        String refreshJti = refreshClaims.getId();

        UserRole role = UserRole.valueOf(roleString);

        // 3) Redis에 저장된 RT와 일치 확인
        String refreshKey = String.format("refresh:%d:%s", memberId, refreshJti);
        String savedRefreshToken = redisTemplate.opsForValue().get(refreshKey);
        if (savedRefreshToken == null || !savedRefreshToken.equals(refreshToken)) {
            throw new CustomAuthException(ErrorStatus.REFRESH_TOKEN_NOT_EQUAL);
        }

        // 4) 기존 RT 삭제 후 새 토큰 발급
        redisTemplate.delete(refreshKey);
        return issueTokens(memberId, username, role, authRealm);
    }

}
