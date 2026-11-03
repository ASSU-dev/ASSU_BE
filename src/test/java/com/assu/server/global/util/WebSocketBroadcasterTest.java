package com.assu.server.global.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class WebSocketBroadcasterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SimpMessagingTemplate messagingTemplate;
    private StringRedisTemplate redisTemplate;
    private WebSocketBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        redisTemplate = mock(StringRedisTemplate.class);
        broadcaster = new WebSocketBroadcaster(messagingTemplate, redisTemplate, objectMapper);
    }

    @AfterEach
    void tearDown() {
        broadcaster.shutdown();
    }

    @Test
    void send_whenRedisFails_thenStillDeliversLocally() {
        // given
        doThrow(new RuntimeException("redis down")).when(redisTemplate).convertAndSend(anyString(), anyString());
        Map<String, Object> payload = Map.of("roomId", 26);

        // when
        broadcaster.send("/sub/chat/26", payload);

        // then
        verify(messagingTemplate).convertAndSend("/sub/chat/26", payload);
        verify(redisTemplate, timeout(1000)).convertAndSend(eq(WebSocketBroadcaster.CHANNEL), anyString());
    }

    @Test
    void onMessage_whenPublishedByOtherInstance_thenDeliversToDestination() throws Exception {
        // given
        String body = """
                {"instanceId":"other","destination":"/certification/progress/7","payload":{"type":"completed","count":3}}
                """;

        // when
        broadcaster.onMessage(redisMessage(body), null);

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/certification/progress/7"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(objectMapper.readTree("{\"type\":\"completed\",\"count\":3}"));
    }

    @Test
    void onMessage_whenPublishedBySelf_thenIgnored() {
        // given
        broadcaster.send("/sub/chat/26", Map.of("roomId", 26));
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, timeout(1000)).convertAndSend(eq(WebSocketBroadcaster.CHANNEL), captor.capture());

        // when
        broadcaster.onMessage(redisMessage(captor.getValue()), null);

        // then
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(JsonNode.class));
    }

    private DefaultMessage redisMessage(String body) {
        return new DefaultMessage(
                WebSocketBroadcaster.CHANNEL.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }
}
