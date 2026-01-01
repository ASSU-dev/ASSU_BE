package com.assu.server.domain.chat.dto;

import com.assu.server.domain.chat.entity.ChattingRoom;
import com.assu.server.domain.chat.entity.Message;
import com.assu.server.domain.chat.entity.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.protobuf.Enum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
        public static CreateChatRoomResponseDTO toCreateChatRoomIdDTO(ChattingRoom room) {
            return new CreateChatRoomResponseDTO(
                    room.getId(),
                    room.getPartner().getName(),
                    room.getAdmin().getName(),
                    true
            );
        }
        public static CreateChatRoomResponseDTO toEnterChatRoomDTO(ChattingRoom room) {
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
        public SendMessageResponseDTO withUnreadCountForSender(Integer count) {
            return new SendMessageResponseDTO(
                    messageId, roomId, senderId, receiverId, message, messageType, sentAt, count
            );
        }

        public static SendMessageResponseDTO toSendMessageDTO(
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
        public static ChatHistoryResponseDTO toChatHistoryDTO(
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

