package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficeChatBlockDTO;
import com.assu.server.domain.backoffice.service.BackofficeChatBlockService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/chat")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeChatController {

    private final BackofficeChatBlockService backofficeChatBlockService;

    @BackofficeAudited(action = "CHAT_BLOCK_BETWEEN", targetId = "#request.blockerId() + '-' + #request.blockedId()")
    @Operation(
            summary = "두 멤버 간 채팅 차단 API",
            description = "- 특정 두 멤버 사이의 채팅을 차단합니다.\n" +
                    "- 이미 차단 관계가 있으면 409(CONFLICT)를 반환합니다.\n\n" +
                    "**Request Body:**\n" +
                    "- `blockerId` (Long, required): 멤버 A ID\n" +
                    "- `blockedId` (Long, required): 멤버 B ID"
    )
    @PostMapping("/block/between")
    public BaseResponse<BackofficeChatBlockDTO.BlockBetweenMembersResponseDTO> blockBetweenMembers(
            @RequestBody @Valid BackofficeChatBlockDTO.BlockBetweenMembersRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK,
                backofficeChatBlockService.blockBetweenMembers(request.blockerId(), request.blockedId()));
    }

    @BackofficeAudited(action = "CHAT_UNBLOCK_BETWEEN", targetId = "#memberAId + '-' + #memberBId")
    @Operation(
            summary = "두 멤버 간 채팅 차단 해제 API",
            description = "- 특정 두 멤버 사이의 채팅 차단을 해제합니다.\n" +
                    "- 차단 관계가 없으면 400(BAD_REQUEST)을 반환합니다.\n\n" +
                    "**Query Params:**\n" +
                    "- `memberAId` (Long, required): 멤버 A ID\n" +
                    "- `memberBId` (Long, required): 멤버 B ID"
    )
    @DeleteMapping("/block/between")
    public BaseResponse<BackofficeChatBlockDTO.BlockBetweenMembersResponseDTO> unblockBetweenMembers(
            @RequestParam Long memberAId,
            @RequestParam Long memberBId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK,
                backofficeChatBlockService.unblockBetweenMembers(memberAId, memberBId));
    }

    @BackofficeAudited(action = "CHAT_BLOCK_MEMBER", targetId = "#memberId")
    @Operation(
            summary = "멤버 채팅 전체 차단 API",
            description = "- 특정 멤버의 채팅 기능 자체를 차단합니다.\n" +
                    "- 이미 차단된 경우 409(CONFLICT)를 반환합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `memberId` (Long, required): 차단할 멤버 ID"
    )
    @PostMapping("/block/member/{memberId}")
    public BaseResponse<BackofficeChatBlockDTO.MemberChatBlockResponseDTO> blockMemberChat(
            @PathVariable Long memberId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK,
                backofficeChatBlockService.blockMemberChat(memberId));
    }

    @BackofficeAudited(action = "CHAT_UNBLOCK_MEMBER", targetId = "#memberId")
    @Operation(
            summary = "멤버 채팅 전체 차단 해제 API",
            description = "- 특정 멤버의 채팅 전체 차단을 해제합니다.\n" +
                    "- 차단 상태가 아닌 경우 400(BAD_REQUEST)을 반환합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `memberId` (Long, required): 차단 해제할 멤버 ID"
    )
    @DeleteMapping("/block/member/{memberId}")
    public BaseResponse<BackofficeChatBlockDTO.MemberChatBlockResponseDTO> unblockMemberChat(
            @PathVariable Long memberId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK,
                backofficeChatBlockService.unblockMemberChat(memberId));
    }
}