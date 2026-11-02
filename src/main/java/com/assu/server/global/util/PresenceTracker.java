package com.assu.server.global.util;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class PresenceTracker {

    private static final String KEY_PREFIX = "chat:presence:";
    private static final Duration ENTRY_TTL = Duration.ofSeconds(90);
    private static final Duration KEY_TTL = Duration.ofSeconds(120);
    private static final long REDIS_READ_TIMEOUT_MS = 300;

    private record Subscription(Long memberId, Long roomId) {}

    private final StringRedisTemplate redisTemplate;
    private final String instanceId = UUID.randomUUID().toString();
    // "sessionId:subscriptionId" -> 구독. 같은 사용자가 여러 세션/구독으로 같은 방에 있어도 구독 단위로 센다.
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    // Redis가 멈춰도 STOMP/채팅 스레드가 묶이지 않도록 Redis 작업은 전용 스레드 하나에서만 처리하고, 밀리면 버린다.
    private final ThreadPoolExecutor redisExecutor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r, "chat-presence-redis");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy()
    );

    public PresenceTracker(MeterRegistry meterRegistry, StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        Gauge.builder("chat.active.sessions", subscriptions,
                        subs -> subs.keySet().stream().map(PresenceTracker::sessionIdOf).distinct().count())
                .register(meterRegistry);
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent e) {
        var acc = StompHeaderAccessor.wrap(e.getMessage());
        Long roomId = parseRoomId(acc.getDestination());
        Principal user = e.getUser() != null ? e.getUser() : acc.getUser();
        Long memberId = memberIdFrom(user);
        log.info("[PresenceTracker] onSubscribe dest={} eUser={} accUser={} -> memberId={} roomId={}",
                acc.getDestination(), e.getUser(), acc.getUser(), memberId, roomId);
        if (roomId == null || memberId == null || acc.getSubscriptionId() == null) return;

        String subKey = acc.getSessionId() + ":" + acc.getSubscriptionId();
        Subscription sub = new Subscription(memberId, roomId);
        subscriptions.put(subKey, sub);
        runOnRedis(() -> addToRedis(subKey, sub));

        log.debug("SUB: member {} -> room {}", memberId, roomId);
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent e) {
        var acc = StompHeaderAccessor.wrap(e.getMessage());
        String subKey = acc.getSessionId() + ":" + acc.getSubscriptionId();

        Subscription sub = subscriptions.remove(subKey);
        if (sub == null) return;
        runOnRedis(() -> removeFromRedis(subKey, sub));

        log.debug("UNSUB: member {} <- room {}", sub.memberId(), sub.roomId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) {
        String prefix = e.getSessionId() + ":";
        subscriptions.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .toList()
                .forEach(key -> {
                    Subscription sub = subscriptions.remove(key);
                    if (sub != null) runOnRedis(() -> removeFromRedis(key, sub));
                });
        log.debug("DISCONNECT: session {}", e.getSessionId());
    }

    public boolean isInRoom(Long memberId, Long roomId) {
        Subscription target = new Subscription(memberId, roomId);
        // ponytail: 로컬 구독 전체를 훑는다. 파드당 동시 채팅 구독이 수만 단위가 되면 (member, room) 카운트 맵으로 바꾼다.
        if (subscriptions.containsValue(target)) return true;
        return isInRoomOnOtherInstance(target);
    }

    @Scheduled(fixedDelay = 30_000)
    public void refreshRedisEntries() {
        Map<String, Subscription> snapshot = Map.copyOf(subscriptions);
        if (snapshot.isEmpty()) return;
        runOnRedis(() -> snapshot.forEach(this::addToRedis));
    }

    @PreDestroy
    public void shutdown() {
        Map<String, Subscription> snapshot = Map.copyOf(subscriptions);
        runOnRedis(() -> snapshot.forEach(this::removeFromRedis));
        redisExecutor.shutdown();
        try {
            redisExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isInRoomOnOtherInstance(Subscription target) {
        try {
            return CompletableFuture
                    .supplyAsync(() -> hasLiveEntryFromOtherInstance(target), redisExecutor)
                    .get(REDIS_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            log.warn("[PresenceTracker] Redis presence 조회 실패, 방에 없음으로 처리. memberId={} roomId={} cause={}",
                    target.memberId(), target.roomId(), ex.toString());
            return false;
        }
    }

    private boolean hasLiveEntryFromOtherInstance(Subscription target) {
        Set<String> members = redisTemplate.opsForZSet()
                .rangeByScore(redisKey(target), System.currentTimeMillis(), Double.POSITIVE_INFINITY);
        if (members == null) return false;
        return members.stream().anyMatch(m -> !m.startsWith(instanceId + ":"));
    }

    private void addToRedis(String subKey, Subscription sub) {
        String key = redisKey(sub);
        long now = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(key, redisMember(subKey), now + ENTRY_TTL.toMillis());
        redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now);
        redisTemplate.expire(key, KEY_TTL);
    }

    private void removeFromRedis(String subKey, Subscription sub) {
        redisTemplate.opsForZSet().remove(redisKey(sub), redisMember(subKey));
    }

    private void runOnRedis(Runnable task) {
        try {
            redisExecutor.execute(() -> {
                try {
                    task.run();
                } catch (Exception ex) {
                    log.warn("[PresenceTracker] Redis presence 갱신 실패 cause={}", ex.toString());
                }
            });
        } catch (Exception ex) {
            log.warn("[PresenceTracker] Redis presence 작업 등록 실패 cause={}", ex.toString());
        }
    }

    private String redisKey(Subscription sub) {
        return KEY_PREFIX + sub.roomId() + ":" + sub.memberId();
    }

    private String redisMember(String subKey) {
        return instanceId + ":" + subKey;
    }

    private static String sessionIdOf(String subKey) {
        return subKey.substring(0, subKey.lastIndexOf(':'));
    }

    private Long parseRoomId(String dest) {
        if (dest == null) return null;
        String[] p = dest.split("/");
        if (p.length != 4 || !"chat".equals(p[2])) return null;
        try {
            return Long.valueOf(p[3]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long memberIdFrom(Principal user) {
        if (user == null) return null;
        return Long.valueOf(user.getName());
    }
}
