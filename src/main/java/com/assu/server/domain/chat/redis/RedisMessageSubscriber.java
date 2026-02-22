package com.assu.server.domain.chat.redis;

import com.assu.server.domain.chat.dto.ChatResponseDTO;
import com.assu.server.domain.chat.dto.ChatRoomUpdateDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // RedisTemplate의 Serializer를 통해 byte[]를 String으로 변환
            String publishMessage = redisTemplate.getStringSerializer().deserialize(message.getBody());
            String channel = redisTemplate.getStringSerializer().deserialize(message.getChannel());

            if (channel == null || publishMessage == null) return;

            if (channel.startsWith("chat.room.")) {
                // 채팅방 메시지
                handleChatMessage(channel, publishMessage);
            } else if (channel.startsWith("user.update.")) {
                // 채팅 목록 업데이트
                handleUserUpdate(channel, publishMessage);
            }
        } catch (Exception e) {
            log.error("Redis 메시지 처리 실패 {}", e.getMessage());
        }
    }

    private void handleChatMessage(String channel, String jsonMessage) throws Exception {
        String roomId = channel.substring("chat.room.".length());
        ChatResponseDTO.SendMessageResponseDTO messageDto =
                objectMapper.readValue(jsonMessage, ChatResponseDTO.SendMessageResponseDTO.class);
        messagingTemplate.convertAndSend("/sub/chat/" + roomId, messageDto);
    }

    private void handleUserUpdate(String channel, String jsonMessage) throws Exception {
        String userId = channel.substring("user.update.".length());
        ChatRoomUpdateDTO updateDTO =
                objectMapper.readValue(jsonMessage, ChatRoomUpdateDTO.class);
        messagingTemplate.convertAndSend("/sub/chat/list/" + userId, updateDTO);
    }
}
