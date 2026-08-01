package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeChatBlockDTO;

public interface BackofficeChatBlockService {
    BackofficeChatBlockDTO.BlockBetweenMembersResponseDTO blockBetweenMembers(Long memberAId, Long memberBId);
    BackofficeChatBlockDTO.BlockBetweenMembersResponseDTO unblockBetweenMembers(Long memberAId, Long memberBId);
    BackofficeChatBlockDTO.MemberChatBlockResponseDTO blockMemberChat(Long memberId);
    BackofficeChatBlockDTO.MemberChatBlockResponseDTO unblockMemberChat(Long memberId);
}