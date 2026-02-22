package com.assu.server.domain.chat.dto;

import com.assu.server.domain.chat.entity.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ChatMessageDTO(
        @JsonIgnore
        Long roomId,
        // 메시지 삭제 시 사용 가능
        Long messageId,

        String message,
        LocalDateTime sendTime,

        @JsonProperty("unreadCountForSender")
        Integer unreadCount,

        @JsonProperty("isRead")
        boolean isRead,

        @JsonProperty("isMyMessage")
        boolean isMyMessage,

        MessageType messageType
){
}
