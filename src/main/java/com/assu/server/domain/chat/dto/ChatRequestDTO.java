package com.assu.server.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;

public class ChatRequestDTO {

    @Schema(description = "채팅방 생성 요청 DTO")
    public record CreateChatRoomRequestDTO(
            @Schema(description = "사장님 id", example = "2")
            @NotNull
            Long adminId,

            @Schema(description = "파트너 id", example = "12")
            @NotNull
            Long partnerId
    ) {}

    @Schema(description = "채팅 메시지 요청 DTO")
    public record ChatMessageRequestDTO(
            @Schema(description = "채팅방 id", example = "1")
            @NotNull
            Long roomId,

            @Schema(description = "보내는 사람 id", example = "12")
            @NotNull
            Long senderId,

            @Schema(description = "받는 사람 id", example = "2")
            @NotNull
            Long receiverId,

            @Schema(description = "메시지 내용", example = "안녕하세요")
            @NotNull
            String message
    ) {}

}
