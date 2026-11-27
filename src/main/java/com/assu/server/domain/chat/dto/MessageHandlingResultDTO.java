package com.assu.server.domain.chat.dto;

public record MessageHandlingResultDTO(
        ChatResponseDTO.SendMessageResponseDTO sendMessageResponseDTO,
        ChatRoomUpdateDTO chatRoomUpdateDTO,
        Long receiverId
) {
    public static MessageHandlingResultDTO of(ChatResponseDTO.SendMessageResponseDTO sendMessageDTO) {
        return new MessageHandlingResultDTO(sendMessageDTO, null, null);
    }

    public static MessageHandlingResultDTO withUpdates(ChatResponseDTO.SendMessageResponseDTO sendMessageDTO, ChatRoomUpdateDTO updateDTO, Long receiverId) {
        return new MessageHandlingResultDTO(sendMessageDTO, updateDTO, receiverId);
    }

    public boolean hasRoomUpdates() {
        return chatRoomUpdateDTO != null;
    }
}