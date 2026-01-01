package com.assu.server.domain.chat.controller;

import com.assu.server.domain.chat.dto.*;
import com.assu.server.domain.chat.repository.MessageRepository;
import com.assu.server.domain.chat.service.BlockService;
import com.assu.server.domain.chat.service.ChatService;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PresenceTracker;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.assu.server.global.apiPayload.BaseResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {
    private final ChatService chatService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final BlockService blockService;

    @Operation(
            summary = "채팅방을 생성하는 API",
            description = "# [v1.0 (2025-08-05)](https://clumsy-seeder-416.notion.site/2241197c19ed80c38871ec77deced713) 채팅방을 생성합니다.\n"+
                    "- adminId: Request Body, Long\n" +
                    "- partnerId: Request Body, Long\n"
    )
    @PostMapping("/rooms")
    public BaseResponse<ChatResponseDTO.CreateChatRoomResponseDTO> createChatRoom(
            @AuthenticationPrincipal PrincipalDetails pd,
            @RequestBody ChatRequestDTO.CreateChatRoomRequestDTO request) {
        Long memberId = pd.getMember().getId();
        return BaseResponse.onSuccess(SuccessStatus._OK, chatService.createChatRoom(request, memberId));
    }

    @Operation(
            summary = "채팅방 목록을 조회하는 API",
            description = "# [v1.0 (2025-08-05)](https://clumsy-seeder-416.notion.site/API-1d71197c19ed819f8f70fb437e9ce62b?p=2241197c19ed816993c3c5ae17d6f099&pm=s) 채팅방 목록을 조회합니다.\n"
    )
    @GetMapping("/rooms")
    public BaseResponse<List<ChatRoomListResultDTO>> getChatRoomList(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        Long memberId = pd.getMember().getId();
        return BaseResponse.onSuccess(SuccessStatus._OK, chatService.getChatRoomList(memberId));
    }

    @Operation(
            summary = "채팅 API",
            description = "# [v1.0 (2025-08-05)](https://clumsy-seeder-416.notion.site/2241197c19ed800eab45c35073761c97?v=2241197c19ed8134b64f000cc26c5d31&p=2371197c19ed80968342e2bc8fe88cee&pm=s) 메시지를 전송합니다.\n"+
                    "- roomId: Request Body, Long\n" +
                    "- senderId: Request Body, Long\n"+
                    "- receiverId: Request Body, Long\n" +
                    "- message: Request Body, String\n" +
                    "- unreadCountForSender: Request Body, int\n"
    )
    @MessageMapping("/send")
    public void handleMessage(@Payload ChatRequestDTO.ChatMessageRequestDTO request) {
        // 1. 서비스 호출
        MessageHandlingResult result = chatService.handleMessage(request);
        // 2. [항상 전송] 채팅방 메시지 전송
        simpMessagingTemplate.convertAndSend("/sub/chat/" + request.roomId(), result.sendMessageResponseDTO());
        // 3. [조건부 전송] 채팅방 목록 업데이트 전송
        if (result.hasRoomUpdates()) {
            simpMessagingTemplate.convertAndSendToUser(
                    result.receiverId().toString(),
                    "/queue/updates",
                    result.chatRoomUpdateDTO()
            );
        }
    }

    @Operation(
            summary = "메시지 읽음 처리 API",
            description = "# [v1.0 (2025-08-05)](https://clumsy-seeder-416.notion.site/2241197c19ed800eab45c35073761c97?v=2241197c19ed8134b64f000cc26c5d31&p=2241197c19ed81ffa771cb18ab157b54&pm=s) 메시지를 읽음처리합니다.\n"+
                    "- roomId: Path Variable, Long\n"
    )
    @PatchMapping("rooms/{roomId}/read")
    public BaseResponse<ChatResponseDTO.ReadMessageResponseDTO> readMessage(
            @AuthenticationPrincipal PrincipalDetails pd,
            @PathVariable Long roomId
    ) {
        Long memberId = pd.getMember().getId();
        ChatResponseDTO.ReadMessageResponseDTO response = chatService.readMessage(roomId, memberId);
        return BaseResponse.onSuccess(SuccessStatus._OK, response);
    }

    @Operation(
            summary = "채팅방 상세 조회 API",
            description = "# [v1.0 (2025-08-05)](https://clumsy-seeder-416.notion.site/2241197c19ed800eab45c35073761c97?v=2241197c19ed8134b64f000cc26c5d31&p=2241197c19ed81399395fd66f73730af&pm=s) 채팅방을 클릭했을 때 메시지를 조회합니다.\n"+
                    "- roomId: Path Variable, Long\n"
    )
    @GetMapping("rooms/{roomId}/messages")
    public BaseResponse<ChatResponseDTO.ChatHistoryResponseDTO> getChatHistory(
            @AuthenticationPrincipal PrincipalDetails pd,
            @PathVariable Long roomId
    ) {
        Long memberId = pd.getMember().getId();
        ChatResponseDTO.ChatHistoryResponseDTO response = chatService.readHistory(roomId, memberId);
        return BaseResponse.onSuccess(SuccessStatus._OK, response);
    }

    @Operation(
            summary = "채팅방을 나가는 API" +
                    "참여자가 2명이면 채팅방이 살아있지만, 이미 한 명이 나갔다면 채팅방이 삭제됩니다.",
            description = "# [v1.0 (2025-08-05)](https://clumsy-seeder-416.notion.site/2241197c19ed800eab45c35073761c97?v=2241197c19ed8134b64f000cc26c5d31&p=2371197c19ed8079a6e1c2331cb4f534&pm=s) 채팅방을 나갑니다.\n"+
                    "- roomId: Path Variable, Long\n"
    )
    @DeleteMapping("rooms/{roomId}/leave")
    public BaseResponse<ChatResponseDTO.LeaveChattingRoomResponseDTO> leaveChattingRoom(
            @AuthenticationPrincipal PrincipalDetails pd,
            @PathVariable Long roomId
    ) {
        Long memberId = pd.getMember().getId();
        return BaseResponse.onSuccess(SuccessStatus._OK, chatService.leaveChattingRoom(roomId, memberId));
    }

    @Operation(
            summary = "상대방을 차단하는 API" +
                    "상대방을 차단합니다. 메시지를 주고받을 수 없습니다.",
            description = "# [v1.0 (2025-09-25)](https://clumsy-seeder-416.notion.site/2db1197c19ed804ba3dbf57ba36860c4) 상대방을 차단합니다.\n"+
                    "- opponentId: Request Body, Long\n"
    )
    @PostMapping("/block")
    public BaseResponse<BlockResponseDTO.BlockMemberDTO> block(
            @AuthenticationPrincipal PrincipalDetails pd,
            @RequestBody BlockRequestDTO.BlockMemberRequestDTO request
            ) {
        Long memberId = pd.getMember().getId();
        return BaseResponse.onSuccess(SuccessStatus._OK, blockService.blockMember(memberId, request.opponentId()));
    }

    @Operation(
            summary = "상대방을 차단했는지 확인하는 API" +
                    "상대방을 차단했는지 여부를 알려줍니다.",
            description = "# [v1.0 (2025-09-25)](https://clumsy-seeder-416.notion.site/2db1197c19ed80769521eab9660ac53f) 상대방을 차단했는지 검사합니다.\n"+
                    "- opponentId: Request Body, Long\n"
    )
    @GetMapping("/check/block/{opponentId}")
    public BaseResponse<BlockResponseDTO.CheckBlockMemberDTO> checkBlock(
            @AuthenticationPrincipal PrincipalDetails pd,
            @PathVariable Long opponentId
    ) {
        Long memberId = pd.getMember().getId();
        return BaseResponse.onSuccess(SuccessStatus._OK, blockService.checkBlock(memberId, opponentId));
    }

    @Operation(
            summary = "상대방을 차단 해제하는 API" +
                    "상대방을 차단해제합니다. 앞으로 다시 메시지를 주고받을 수 있습니다.",
            description = "# [v1.0 (2025-09-25)](https://clumsy-seeder-416.notion.site/2db1197c19ed80b6a93fcbe277fc934c?pvs=74) 상대방을 차단 해제합니다.\n"+
                    "- opponentId: Request Body, Long\n"
    )
    @DeleteMapping("/unblock")
    public BaseResponse<BlockResponseDTO.BlockMemberDTO> unblock(
            @AuthenticationPrincipal PrincipalDetails pd,
            @RequestParam Long opponentId
    ) {
        Long memberId = pd.getMember().getId();
        return BaseResponse.onSuccess(SuccessStatus._OK, blockService.unblockMember(memberId, opponentId));
    }

    @Operation(
            summary = "차단한 대상을 조회합니다." +
                    "본인이 차단한 대상을 모두 조회합니다.",
            description = "# [v1.0 (2025-09-25)](https://clumsy-seeder-416.notion.site/2db1197c19ed8000b047d9857bcbbb2f) 차단한 대상을 조회합니다..\n"
    )
    @GetMapping("/blockList")
    public BaseResponse<List<BlockResponseDTO.BlockMemberDTO>> getBlockList(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        Long memberId = pd.getMember().getId();
        return BaseResponse.onSuccess(SuccessStatus._OK, blockService.getMyBlockList(memberId));
    }

}
