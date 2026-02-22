package com.assu.server.domain.chat.dto;

import com.assu.server.domain.chat.entity.ChattingRoom;
import com.assu.server.domain.chat.entity.Message;
import com.assu.server.domain.chat.entity.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public class ChatResponseDTO {

    // 채팅방 목록 조회
    public record CreateChatRoomResponseDTO(
            Long roomId,
            String adminViewName,
            String partnerViewName,
            Boolean isNew
    ) {
        public static CreateChatRoomResponseDTO fromNewRoom(ChattingRoom room) {
            return new CreateChatRoomResponseDTO(
                    room.getId(),
                    room.getPartner().getName(),
                    room.getAdmin().getName(),
                    true
            );
        }
        public static CreateChatRoomResponseDTO fromExistingRoom(ChattingRoom room) {
            return new CreateChatRoomResponseDTO(
                    room.getId(),
                    room.getPartner().getName(),
                    room.getAdmin().getName(),
                    false
            );
        }
    }

    // 메시지 전송
    public record SendMessageResponseDTO(
        Long messageId,
        Long roomId,
        Long senderId,
        Long receiverId,
        String message,
        MessageType messageType,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime sentAt,
        Integer unreadCountForSender
    ) {

        public static SendMessageResponseDTO from(
                Message message
        ) {
            return new SendMessageResponseDTO(
                    message.getId(),
                    message.getChattingRoom().getId(),
                    message.getSender().getId(),
                    message.getReceiver().getId(),
                    message.getMessage(),
                    message.getType(),
                    message.getCreatedAt(),
                    message.getUnreadCount()
            );


        }
    }

    // 메시지 읽음 처리
    public record ReadMessageResponseDTO(
        Long roomId,
        Long readerId,
        List<Long> readMessagesId,
        int readCount,
        boolean isRead
    ) {}

    // 채팅방 들어갔을 때 조회
    public record ChatHistoryResponseDTO(
            Long roomId,
            List<ChatMessageDTO> messages
    ) {
        public static ChatHistoryResponseDTO of(
                Long roomId,
                List<ChatMessageDTO> messages) {
            return new ChatHistoryResponseDTO(
                    roomId,
                    messages
            );
        }
    }

    public record LeaveChattingRoomResponseDTO(
            Long roomId,
            boolean isLeftSuccessfully,
            boolean isRoomDeleted
    ) {

    }
}

