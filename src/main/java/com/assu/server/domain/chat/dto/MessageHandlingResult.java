package com.assu.server.domain.chat.dto;

public record MessageHandlingResult(
        ChatResponseDTO.SendMessageResponseDTO sendMessageResponseDTO,
        ChatRoomUpdateDTO chatRoomUpdateDTO,
        Long receiverId
) {
    public static MessageHandlingResult of(ChatResponseDTO.SendMessageResponseDTO sendMessageDTO) {
        return new MessageHandlingResult(sendMessageDTO, null, null);
    }

    public static MessageHandlingResult withUpdates(ChatResponseDTO.SendMessageResponseDTO sendMessageDTO, ChatRoomUpdateDTO updateDTO, Long receiverId) {
        return new MessageHandlingResult(sendMessageDTO, updateDTO, receiverId);
    }

    public boolean hasRoomUpdates() {
        return chatRoomUpdateDTO != null;
    }
}