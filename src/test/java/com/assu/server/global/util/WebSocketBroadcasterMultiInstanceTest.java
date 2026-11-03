package com.assu.server.global.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WebSocketBroadcasterMultiInstanceTest {

    record ChatPayload(Long roomId, String message, LocalDateTime sentAt, Boolean isRead) {}

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final List<WebSocketBroadcaster> subscribers = new CopyOnWriteArrayList<>();
    private StringRedisTemplate sharedRedis;
    private SimpMessagingTemplate podABroker;
    private SimpMessagingTemplate podBBroker;
    private WebSocketBroadcaster podA;
    private WebSocketBroadcaster podB;

    @BeforeEach
    void setUp() {
        sharedRedis = mock(StringRedisTemplate.class);
        doAnswer(inv -> {
            DefaultMessage message = new DefaultMessage(
                    inv.<String>getArgument(0).getBytes(StandardCharsets.UTF_8),
                    inv.<String>getArgument(1).getBytes(StandardCharsets.UTF_8));
            subscribers.forEach(s -> s.onMessage(message, null));
            return 1L;
        }).when(sharedRedis).convertAndSend(eq(WebSocketBroadcaster.CHANNEL), anyString());

        podABroker = mock(SimpMessagingTemplate.class);
        podBBroker = mock(SimpMessagingTemplate.class);
        podA = new WebSocketBroadcaster(podABroker, sharedRedis, objectMapper);
        podB = new WebSocketBroadcaster(podBBroker, sharedRedis, objectMapper);
        subscribers.add(podA);
        subscribers.add(podB);
    }

    @AfterEach
    void tearDown() {
        podA.shutdown();
        podB.shutdown();
    }

    @Test
    void send_whenTwoPods_thenEachPodDeliversExactlyOnceWithSameJson() throws Exception {
        // given
        ChatPayload payload = new ChatPayload(26L, "안녕하세요", LocalDateTime.of(2026, 11, 3, 9, 30, 15), null);

        // when
        podA.send("/sub/chat/26", payload);

        // then
        verify(podABroker, times(1)).convertAndSend("/sub/chat/26", (Object) payload);
        ArgumentCaptor<Object> relayed = ArgumentCaptor.forClass(Object.class);
        verify(podBBroker, timeout(2000).times(1)).convertAndSend(eq("/sub/chat/26"), relayed.capture());
        assertThat(objectMapper.writeValueAsString(relayed.getValue()))
                .isEqualTo(objectMapper.writeValueAsString(payload));
        verify(podABroker, times(1)).convertAndSend(anyString(), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void send_whenRedisDown_thenSendingPodStillDeliversAndOtherPodMisses() {
        // given
        doThrow(new RuntimeException("redis down")).when(sharedRedis).convertAndSend(anyString(), anyString());

        // when
        podA.send("/certification/progress/7", new ChatPayload(7L, "progress", null, null));

        // then
        verify(podABroker, times(1)).convertAndSend(eq("/certification/progress/7"), org.mockito.ArgumentMatchers.<Object>any());
        verify(sharedRedis, timeout(2000)).convertAndSend(eq(WebSocketBroadcaster.CHANNEL), anyString());
        verify(podBBroker, never()).convertAndSend(anyString(), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void send_whenManyMessages_thenOtherPodReceivesInOrder() {
        // when
        for (int i = 0; i < 50; i++) {
            podA.send("/sub/chat/26", new ChatPayload(26L, "m" + i, null, null));
        }

        // then
        ArgumentCaptor<Object> relayed = ArgumentCaptor.forClass(Object.class);
        verify(podBBroker, timeout(3000).times(50)).convertAndSend(eq("/sub/chat/26"), relayed.capture());
        List<String> messages = relayed.getAllValues().stream()
                .map(v -> objectMapper.convertValue(v, ChatPayload.class).message())
                .toList();
        assertThat(messages).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, 50).mapToObj(i -> "m" + i).toList());
    }
}
