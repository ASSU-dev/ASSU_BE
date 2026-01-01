package com.assu.server.domain.chat.dto;

import com.assu.server.domain.chat.entity.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
