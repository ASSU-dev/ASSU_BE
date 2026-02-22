package com.assu.server.domain.chat.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChattingRedisPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    // 채팅방 메시지 발행
    public void publishChatMessage(Long roomId, Object message) {
        redisTemplate.convertAndSend("chat.room." + roomId, message);
    }

    // 채팅 목록 업데이트 발행
    public void publishChatRoomUpdate(Long userId, Object updateDto) {
        redisTemplate.convertAndSend("user.update." + userId, updateDto);
    }
}
