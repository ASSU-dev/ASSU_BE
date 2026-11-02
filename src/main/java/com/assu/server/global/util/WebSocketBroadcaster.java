package com.assu.server.global.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WebSocketBroadcaster implements MessageListener {

    public static final String CHANNEL = "ws.broadcast";

    private record Envelope(String instanceId, String destination, JsonNode payload) {}

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String instanceId = UUID.randomUUID().toString();
    // Redis가 멈춰도 요청 스레드가 묶이지 않도록 발행은 전용 스레드 하나에서만 처리하고, 밀리면 버린다.
    private final ThreadPoolExecutor publishExecutor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r, "ws-broadcast-publish");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy()
    );

    public WebSocketBroadcaster(SimpMessagingTemplate messagingTemplate,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
        publishToOtherInstances(destination, payload);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Envelope envelope = objectMapper.readValue(message.getBody(), Envelope.class);
            if (instanceId.equals(envelope.instanceId())) return;
            messagingTemplate.convertAndSend(envelope.destination(), envelope.payload());
        } catch (Exception e) {
            log.error("[WebSocketBroadcaster] 다른 파드 메시지 전달 실패 cause={}", e.toString());
        }
    }

    @PreDestroy
    public void shutdown() {
        publishExecutor.shutdown();
    }

    private void publishToOtherInstances(String destination, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(
                    new Envelope(instanceId, destination, objectMapper.valueToTree(payload)));
            publishExecutor.execute(() -> {
                try {
                    redisTemplate.convertAndSend(CHANNEL, json);
                } catch (Exception e) {
                    log.warn("[WebSocketBroadcaster] Redis 발행 실패 destination={} cause={}", destination, e.toString());
                }
            });
        } catch (Exception e) {
            log.warn("[WebSocketBroadcaster] Redis 발행 준비 실패 destination={} cause={}", destination, e.toString());
        }
    }
}
