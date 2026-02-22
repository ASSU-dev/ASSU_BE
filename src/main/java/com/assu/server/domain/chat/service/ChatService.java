package com.assu.server.domain.chat.service;

import com.assu.server.domain.chat.dto.ChatRequestDTO;
import com.assu.server.domain.chat.dto.ChatResponseDTO;
import com.assu.server.domain.chat.dto.ChatRoomListResultDTO;
import com.assu.server.domain.chat.dto.MessageHandlingResult;

import java.util.List;

public interface ChatService {
    List<ChatRoomListResultDTO> getChatRoomList(Long memberId);
    ChatResponseDTO.CreateChatRoomResponseDTO createChatRoom(ChatRequestDTO.CreateChatRoomRequestDTO request, Long memberId);
    MessageHandlingResult handleMessage(ChatRequestDTO.ChatMessageRequestDTO request);
    ChatResponseDTO.ReadMessageResponseDTO readMessage(Long roomId, Long memberId);
    ChatResponseDTO.ChatHistoryResponseDTO readHistory(Long roomId, Long memberId);
    ChatResponseDTO.LeaveChattingRoomResponseDTO leaveChattingRoom(Long roomId, Long memberId);
    ChatResponseDTO.SendMessageResponseDTO sendGuideMessage(ChatRequestDTO.ChatMessageRequestDTO request);
}
