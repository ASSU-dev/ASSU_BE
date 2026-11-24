package com.assu.server.global.config;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.util.ClientIpResolver;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

// 번호·이메일 기준 제한은 값을 바꿔 가며 보내는 요청을 못 막으므로 요청자 IP 기준 상한을 따로 둔다
@Slf4j
@RequiredArgsConstructor
public class IpRateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "ip-limit:";

    // 이름 조회(DNS)가 일어나지 않도록 IP 리터럴 형태만 InetAddress로 넘긴다
    private static final Pattern IP_LITERAL =
            Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}|[0-9a-fA-F:]*:[0-9a-fA-F:.]*");

    private static final RedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final String bucket;
    private final int maxRequests;
    private final Duration window;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String clientIp = ClientIpResolver.resolve(request);
        // 사설·루프백 주소는 프록시 주소가 넘어온 것이라, 세면 모든 사용자가 한도 하나를 나눠 쓰게 된다
        if (!isPublicAddress(clientIp)) {
            return true;
        }
        if (exceedsLimit(clientIp)) {
            meterRegistry.counter("auth.ip.blocked", "bucket", bucket).increment();
            log.warn("[IP_RATE_LIMITED] bucket={} ip={}", bucket, clientIp);
            throw new CustomAuthException(ErrorStatus.AUTH_REQUESTS_EXCEEDED);
        }
        return true;
    }

    private boolean exceedsLimit(String clientIp) {
        try {
            Long count = redisTemplate.execute(INCREMENT_WITH_TTL_SCRIPT,
                    List.of(KEY_PREFIX + bucket + ":" + clientIp), String.valueOf(window.toSeconds()));
            return count != null && count > maxRequests;
        } catch (RuntimeException exception) {
            // Redis 장애가 로그인·인증 전체 차단으로 번지지 않도록 통과시킨다(fail-open)
            log.warn("Redis IP 요청 제한 처리 실패로 fail-open 처리. bucket={}", bucket, exception);
            meterRegistry.counter("auth.ip.limit.failopen").increment();
            return false;
        }
    }

    private static boolean isPublicAddress(String ip) {
        if (ip == null || !IP_LITERAL.matcher(ip).matches()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return !(address.isSiteLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress());
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
