package com.assu.server.domain.chat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public record ChatRoomUpdateDTO(
        Long roomId,
        String lastMessage,
        LocalDateTime lastMessageTime,
        Long unreadCount // 해당 채팅방의 총 안읽은 메시지 수
) {
    public static ChatRoomUpdateDTO toChatRoomUpdateDTO(
            Long roomId,
            String lastMessage,
            LocalDateTime lastMessageTime,
            Long unreadCount
    ) {
        return new ChatRoomUpdateDTO(
                roomId,
                lastMessage,
                lastMessageTime,
                unreadCount
        );
    }
}
