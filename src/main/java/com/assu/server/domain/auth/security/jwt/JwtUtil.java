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
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JWT 발급/검증 및 Authentication 복원 유틸리티.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class JwtUtil {

    private static final String REVOKED_BEFORE_KEY_PREFIX = "revoked-before:";
    private static final String REFRESH_GRACE_KEY_PREFIX = "refresh-grace:";
    // 회전 직후 같은 RT로 들어온 재시도·동시 요청에 방금 발급한 토큰을 그대로 돌려주는 시간. 탈취된 RT 재사용 창을 줄이려 짧게 둔다
    private static final Duration REFRESH_GRACE = Duration.ofSeconds(3);
    private static final String GRACE_PENDING = "PENDING";
    private static final int GRACE_POLL_ATTEMPTS = 20;
    private static final long GRACE_POLL_INTERVAL_MILLIS = 50;
    private static final String CLAIMED = "CLAIMED";
    private static final String MISMATCH = "MISMATCH";
    // RT 소비와 유예 자리 선점을 한 번에 해서, 회전 중인 요청이 있을 때만 다른 요청이 기다리게 한다
    private static final RedisScript<String> CLAIM_REFRESH_SCRIPT = new DefaultRedisScript<>("""
            local saved = redis.call('GET', KEYS[1])
            if not saved then
                return 'MISSING'
            end
            if saved ~= ARGV[1] then
                return 'MISMATCH'
            end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            return 'CLAIMED'
            """, String.class);
    private static final int SCAN_BATCH_SIZE = 100;
    private static final String ISSUED_AT_MILLIS_CLAIM = "iatMillis";

    public static final String AUD_APP = "app";
    public static final String AUD_BACKOFFICE = "backoffice";

    @Value("${jwt.secret}")
    public String secretKey;

    @Value("${jwt.access-valid-seconds:3600}")      // 1시간 기본
    private int accessValidSeconds;

    @Value("${jwt.backoffice-access-valid-seconds:1800}")
    private int backofficeAccessValidSeconds;

    @Value("${jwt.refresh-valid-seconds:1209600}")  // 14일 기본
    private int refreshValidSeconds;

    private final MemberRepository memberRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

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
        long issuedAtMillis = System.currentTimeMillis();
        Map<String, Object> tokenClaims = new HashMap<>(claims);
        tokenClaims.put(ISSUED_AT_MILLIS_CLAIM, issuedAtMillis);
        return Jwts.builder()
                .setHeader(Map.of("typ", "JWT"))
                .setClaims(tokenClaims)
                .setId(jti)
                .setIssuedAt(new Date(issuedAtMillis))
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
        return issueTokens(memberId, username, role, authRealm, AUD_APP);
    }

    public TokensDTO issueBackofficeTokens(Long memberId, String username, UserRole role, String authRealm) {
        return issueTokens(memberId, username, role, authRealm, AUD_BACKOFFICE);
    }

    public TokensDTO issueTokens(Long memberId, String username, UserRole role, String authRealm, String audience) {
        Map<String, Object> baseClaims = new HashMap<>();
        baseClaims.put("userId", memberId);
        baseClaims.put("username", username);
        baseClaims.put("role", role.name());
        baseClaims.put("authRealm", authRealm);
        baseClaims.put("aud", audience);

        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        int accessSeconds = AUD_BACKOFFICE.equals(audience) ? backofficeAccessValidSeconds : accessValidSeconds;
        String accessToken = generateToken(baseClaims, accessSeconds, accessJti);
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
        Claims claims = validateToken(accessToken);
        return buildAuthentication(claims);
    }

    public void assertAudience(Claims claims, String requiredAudience) {
        String audience = resolveAudience(claims);
        if (!requiredAudience.equals(audience)) {
            throw new CustomAuthException(ErrorStatus.JWT_AUDIENCE_MISMATCH);
        }
    }

    public String resolveAudience(Claims claims) {
        Object aud = claims.get("aud");
        if (aud == null || aud.toString().isBlank()) {
            return AUD_APP;
        }
        return aud.toString();
    }

    public boolean isBackofficeAudience(Claims claims) {
        return AUD_BACKOFFICE.equals(resolveAudience(claims));
    }

    private void assertNotWithdrawn(Member member) {
        if (member.isWithdrawn()) {
            throw new CustomAuthException(ErrorStatus.JWT_ACCESS_TOKEN_REVOKED);
        }
    }

    private Authentication buildAuthentication(Claims claims) {
        Long memberId = ((Number) claims.get("userId")).longValue();
        String roleName = (String) claims.get("role");
        String authRealmName = (String) claims.get("authRealm");

        // DB 조회
        Member member = memberRepository.findWithProfilesById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
        assertNotWithdrawn(member);

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
        Member member = memberRepository.findWithProfilesById(userId)
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
        Boolean exists;
        try {
            exists = redisTemplate.hasKey("blacklist:" + jti);
        } catch (DataAccessException exception) {
            // Redis 장애 시 서명·만료가 이미 검증된 토큰까지 차단하면 서비스 전체가 다운되므로, 노출 창을 액세스 토큰 잔여 만료 시간으로 제한하는 대신 통과시킨다(fail-open)
            log.warn("Redis 블랙리스트 조회 실패로 fail-open 처리. jti={}", jti, exception);
            meterRegistry.counter("auth.blacklist.failopen").increment();
            return;
        }
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
     * 특정 회원의 모든 Refresh 토큰과 회전 유예 기록을 Redis에서 제거 (전역 로그아웃용).
     * KEYS는 실행되는 동안 Redis 전체를 멈추므로 SCAN으로 나눠 조회한다.
     * @param memberId 사용자 ID
     */
    public void removeAllRefreshTokens(Long memberId) {
        deleteByPattern(String.format("refresh:%d:*", memberId));
        deleteByPattern(String.format("%s%d:*", REFRESH_GRACE_KEY_PREFIX, memberId));
    }

    private void deleteByPattern(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
        List<String> batch = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            if (cursor == null) {
                return;
            }
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_BATCH_SIZE) {
                    redisTemplate.delete(batch);
                    batch = new ArrayList<>();
                }
            }
        }
        if (!batch.isEmpty()) {
            redisTemplate.delete(batch);
        }
    }

    /**
     * Refresh 토큰 회전.
     * - Lua 스크립트로 저장된 RT 대조·삭제와 유예 자리 선점을 원자적으로 해, 같은 RT로는 한 번만 회전된다.
     * - 회전 직후 짧은 유예 기간 동안 같은 RT로 다시 들어온 요청(재시도·동시 요청)에는 방금 발급한 토큰을 그대로 돌려준다.
     */
    public TokensDTO rotateRefreshToken(String refreshToken) {
        validateRefreshToken(refreshToken);

        Claims refreshClaims = validateTokenOnlySignature(refreshToken);
        Long memberId = ((Number) refreshClaims.get("userId")).longValue();
        String username = (String) refreshClaims.get("username");
        String roleString = (String) refreshClaims.get("role");
        String authRealm = (String) refreshClaims.get("authRealm");
        String refreshJti = refreshClaims.getId();
        String audience = resolveAudience(refreshClaims);

        UserRole role = UserRole.valueOf(roleString);

        // 탈퇴와 동시에 로그인해 남은 RT로 탈퇴 회원이 토큰을 계속 갱신하지 못하게 한다
        assertNotWithdrawn(memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER)));

        String refreshKey = String.format("refresh:%d:%s", memberId, refreshJti);
        String graceKey = String.format("%s%d:%s", REFRESH_GRACE_KEY_PREFIX, memberId, refreshJti);
        String claimResult = redisTemplate.execute(CLAIM_REFRESH_SCRIPT, List.of(refreshKey, graceKey),
                refreshToken, GRACE_PENDING, String.valueOf(REFRESH_GRACE.toMillis()));

        if (MISMATCH.equals(claimResult)) {
            throw new CustomAuthException(ErrorStatus.REFRESH_TOKEN_NOT_EQUAL);
        }
        if (!CLAIMED.equals(claimResult)) {
            return awaitGraceTokens(graceKey)
                    .orElseThrow(() -> new CustomAuthException(ErrorStatus.REFRESH_TOKEN_NOT_EQUAL));
        }

        TokensDTO tokens = AUD_BACKOFFICE.equals(audience)
                ? issueBackofficeTokens(memberId, username, role, authRealm)
                : issueTokens(memberId, username, role, authRealm);
        rememberGraceTokens(graceKey, tokens);
        return tokens;
    }

    private void rememberGraceTokens(String graceKey, TokensDTO tokens) {
        try {
            redisTemplate.opsForValue().set(graceKey, tokens.accessToken() + " " + tokens.refreshToken(), REFRESH_GRACE);
        } catch (DataAccessException exception) {
            log.warn("Refresh 토큰 회전 유예 기록 저장 실패", exception);
        }
    }

    // 다른 요청이 회전 중(PENDING)일 때만 발급이 끝나길 잠깐 기다리고, 유예 기록이 없으면 바로 거절한다
    private Optional<TokensDTO> awaitGraceTokens(String graceKey) {
        for (int attempt = 0; attempt < GRACE_POLL_ATTEMPTS; attempt++) {
            String saved = redisTemplate.opsForValue().get(graceKey);
            if (saved == null) {
                return Optional.empty();
            }
            if (!GRACE_PENDING.equals(saved)) {
                String[] parts = saved.split(" ", 2);
                return Optional.of(TokensDTO.of(parts[0], parts[1]));
            }
            try {
                Thread.sleep(GRACE_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Optional.empty();
    }

    // ───────────────────────── 회원 단위 Access 토큰 무효화 ─────────────────────────

    /**
     * 특정 회원에게 발급된 모든 Access 토큰을 무효화(탈퇴/강제 탈퇴 시 사용).
     * - Redis에 "revoked-before:{memberId}" 키로 현재 시각을 저장.
     * - TTL은 발급 가능한 Access 토큰의 최대 유효 시간으로 설정.
     * @param memberId 사용자 ID
     */
    public void revokeAllAccessTokensSince(Long memberId) {
        long ttlSeconds = Math.max(accessValidSeconds, backofficeAccessValidSeconds);
        redisTemplate.opsForValue().set(
                REVOKED_BEFORE_KEY_PREFIX + memberId,
                String.valueOf(System.currentTimeMillis()),
                ttlSeconds,
                TimeUnit.SECONDS
        );
    }

    /**
     * Access 토큰이 회원 단위 무효화 시점 이전에 발급되었는지 확인.
     * - Redis 조회 실패 시 정상 요청을 막지 않기 위해 fail-open으로 통과시킨다.
     * @param claims 검증된 Access 토큰의 Claims
     * @throws CustomAuthException 무효화 시점 이후 요청이 거부되어야 하는 경우
     */
    public void assertNotRevoked(Claims claims) {
        Long memberId = ((Number) claims.get("userId")).longValue();
        String revokedBeforeValue;
        try {
            revokedBeforeValue = redisTemplate.opsForValue().get(REVOKED_BEFORE_KEY_PREFIX + memberId);
        } catch (DataAccessException exception) {
            log.warn("Redis 장애로 revoked-before 조회를 건너뜁니다. memberId={}, cause={}", memberId, exception.getMessage());
            return;
        }
        if (revokedBeforeValue == null) {
            return;
        }
        long revokedBeforeMillis = Long.parseLong(revokedBeforeValue);
        Object issuedAtMillisClaim = claims.get(ISSUED_AT_MILLIS_CLAIM);
        long issuedAtMillis = issuedAtMillisClaim != null
                ? ((Number) issuedAtMillisClaim).longValue()
                : claims.getIssuedAt().getTime();
        if (issuedAtMillis < revokedBeforeMillis) {
            throw new CustomAuthException(ErrorStatus.JWT_ACCESS_TOKEN_REVOKED);
        }
    }

}
