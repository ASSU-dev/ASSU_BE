package com.assu.server.domain.chat.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record ChatRoomListResultDTO(
        Long roomId,
        String lastMessage,
        LocalDateTime lastMessageTime,
        Long unreadMessagesCount,
        Long opponentId,
        String opponentName,
        String opponentProfileImage,
        String phoneNumber

) {
    // 채팅방 리스트 아이템 하나
    public static ChatRoomListResultDTO of(
            ChatRoomListResultDTO dto
    ) {
        return new ChatRoomListResultDTO(
                dto.roomId,
                dto.lastMessage,
                dto.lastMessageTime,
                dto.unreadMessagesCount,
                dto.opponentId,
                dto.opponentName,
                dto.opponentProfileImage,
                dto.phoneNumber
        );
    }

    // 채팅방 리스트 변환
    public static List<ChatRoomListResultDTO> ofList(
            List<ChatRoomListResultDTO> dto
    ) {
        return dto.stream()
                .map(ChatRoomListResultDTO::of)
                .collect(Collectors.toList());
    }
}
