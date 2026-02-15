package com.assu.server.domain.chat.dto;

public class ChatRequestDTO {

    public record CreateChatRoomRequestDTO(
            Long adminId,
            Long partnerId
    ) {}

    public record ChatMessageRequestDTO(
            Long roomId,
            Long senderId,
            Long receiverId,
            String message
    ) {}

}
