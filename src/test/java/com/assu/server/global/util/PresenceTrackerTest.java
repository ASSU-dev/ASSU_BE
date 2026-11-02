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
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresenceTrackerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 26L;

    private ZSetOperations<String, String> zSetOperations;
    private PresenceTracker presenceTracker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        presenceTracker = new PresenceTracker(new SimpleMeterRegistry(), redisTemplate);
    }

    @AfterEach
    void tearDown() {
        presenceTracker.shutdown();
    }

    @Test
    void isInRoom_whenOneOfTwoSessionsDisconnects_thenStillInRoom() {
        // given
        presenceTracker.onSubscribe(subscribe("s1", "sub-0", "/sub/chat/" + ROOM_ID));
        presenceTracker.onSubscribe(subscribe("s2", "sub-0", "/sub/chat/" + ROOM_ID));

        // when
        presenceTracker.onDisconnect(disconnect("s1"));

        // then
        assertThat(presenceTracker.isInRoom(MEMBER_ID, ROOM_ID)).isTrue();
    }

    @Test
    void isInRoom_whenAllSubscriptionsRemoved_thenNotInRoom() {
        // given
        presenceTracker.onSubscribe(subscribe("s1", "sub-0", "/sub/chat/" + ROOM_ID));
        presenceTracker.onSubscribe(subscribe("s2", "sub-0", "/sub/chat/" + ROOM_ID));

        // when
        presenceTracker.onUnsubscribe(unsubscribe("s1", "sub-0"));
        presenceTracker.onDisconnect(disconnect("s2"));

        // then
        assertThat(presenceTracker.isInRoom(MEMBER_ID, ROOM_ID)).isFalse();
    }

    @Test
    void isInRoom_whenSubscribedOnOtherInstance_thenInRoom() {
        when(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Set.of("other-instance:s9:sub-0"));

        assertThat(presenceTracker.isInRoom(MEMBER_ID, ROOM_ID)).isTrue();
    }

    @Test
    void isInRoom_whenRedisFails_thenNotInRoom() {
        when(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(presenceTracker.isInRoom(MEMBER_ID, ROOM_ID)).isFalse();
    }

    @Test
    void onSubscribe_whenRoomIdIsNotNumber_thenIgnored() {
        presenceTracker.onSubscribe(subscribe("s1", "sub-0", "/sub/chat/abc"));

        assertThat(presenceTracker.isInRoom(MEMBER_ID, ROOM_ID)).isFalse();
    }

    private SessionSubscribeEvent subscribe(String sessionId, String subId, String destination) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acc.setSessionId(sessionId);
        acc.setSubscriptionId(subId);
        acc.setDestination(destination);
        return new SessionSubscribeEvent(this, message(acc), principal());
    }

    private SessionUnsubscribeEvent unsubscribe(String sessionId, String subId) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        acc.setSessionId(sessionId);
        acc.setSubscriptionId(subId);
        return new SessionUnsubscribeEvent(this, message(acc), principal());
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
