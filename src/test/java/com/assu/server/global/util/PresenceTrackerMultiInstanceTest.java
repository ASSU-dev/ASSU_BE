package com.assu.server.global.util;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresenceTrackerMultiInstanceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 26L;

    private final Map<String, Map<String, Double>> sharedZSets = new ConcurrentHashMap<>();
    private PresenceTracker podA;
    private PresenceTracker podB;

    @BeforeEach
    void setUp() {
        StringRedisTemplate sharedRedis = sharedRedis();
        podA = new PresenceTracker(new SimpleMeterRegistry(), sharedRedis);
        podB = new PresenceTracker(new SimpleMeterRegistry(), sharedRedis);
    }

    @AfterEach
    void tearDown() {
        podA.shutdown();
        podB.shutdown();
    }

    @Test
    void isInRoom_whenReceiverSubscribedOnOtherPod_thenTrue() {
        podA.onSubscribe(subscribe("a-session", "sub-0"));

        assertThat(eventually(() -> podB.isInRoom(MEMBER_ID, ROOM_ID))).isTrue();
    }

    @Test
    void isInRoom_whenReceiverLeftOnOtherPod_thenFalse() {
        // given
        podA.onSubscribe(subscribe("a-session", "sub-0"));
        assertThat(eventually(() -> podB.isInRoom(MEMBER_ID, ROOM_ID))).isTrue();

        // when
        podA.onDisconnect(disconnect("a-session"));

        // then
        assertThat(eventually(() -> !podB.isInRoom(MEMBER_ID, ROOM_ID))).isTrue();
    }

    @Test
    void isInRoom_whenSameMemberOnBothPodsAndOneLeaves_thenStillInRoomOnBoth() {
        // given
        podA.onSubscribe(subscribe("a-session", "sub-0"));
        podB.onSubscribe(subscribe("b-session", "sub-0"));

        // when
        podA.onDisconnect(disconnect("a-session"));

        // then
        assertThat(podB.isInRoom(MEMBER_ID, ROOM_ID)).isTrue();
        assertThat(eventually(() -> podA.isInRoom(MEMBER_ID, ROOM_ID))).isTrue();
    }

    @Test
    void isInRoom_whenOtherPodCrashedAndEntryExpired_thenFalse() {
        String key = "chat:presence:" + ROOM_ID + ":" + MEMBER_ID;
        sharedZSets.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .put("dead-pod:s1:sub-0", (double) (System.currentTimeMillis() - 1));

        assertThat(podB.isInRoom(MEMBER_ID, ROOM_ID)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate sharedRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zSet = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zSet);
        when(zSet.add(anyString(), anyString(), anyDouble())).thenAnswer(inv -> {
            sharedZSets.computeIfAbsent(inv.getArgument(0), k -> new ConcurrentHashMap<>())
                    .put(inv.getArgument(1), inv.getArgument(2));
            return true;
        });
        when(zSet.remove(anyString(), any())).thenAnswer(inv -> {
            Map<String, Double> set = sharedZSets.get(inv.<String>getArgument(0));
            return set == null ? 0L : (set.remove(inv.<String>getArgument(1)) != null ? 1L : 0L);
        });
        when(zSet.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenAnswer(inv -> {
            Map<String, Double> set = sharedZSets.get(inv.<String>getArgument(0));
            if (set == null) return 0L;
            double min = inv.getArgument(1);
            double max = inv.getArgument(2);
            set.values().removeIf(score -> score >= min && score <= max);
            return 0L;
        });
        when(zSet.rangeByScore(anyString(), anyDouble(), anyDouble())).thenAnswer(inv -> {
            Map<String, Double> set = sharedZSets.getOrDefault(inv.<String>getArgument(0), Map.of());
            double min = inv.getArgument(1);
            double max = inv.getArgument(2);
            return set.entrySet().stream()
                    .filter(e -> e.getValue() >= min && e.getValue() <= max)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        });
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        return redis;
    }

    private boolean eventually(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            Thread.onSpinWait();
        }
        return condition.getAsBoolean();
    }

    private SessionSubscribeEvent subscribe(String sessionId, String subId) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acc.setSessionId(sessionId);
        acc.setSubscriptionId(subId);
        acc.setDestination("/sub/chat/" + ROOM_ID);
        return new SessionSubscribeEvent(this, message(acc), principal());
    }

    private SessionDisconnectEvent disconnect(String sessionId) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        acc.setSessionId(sessionId);
        return new SessionDisconnectEvent(this, message(acc), sessionId, CloseStatus.NORMAL, principal());
    }

    private Message<byte[]> message(StompHeaderAccessor acc) {
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    private Principal principal() {
        return () -> String.valueOf(MEMBER_ID);
    }
}
