package com.assu.server.domain.notification.controller;

import com.assu.server.domain.notification.dto.*;
import com.assu.server.domain.notification.entity.NotificationType;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.notification.service.NotificationQueryService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;
import java.util.Map;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationQueryService notificationQueryService;
    private final NotificationCommandService notificationCommandService;

    @Operation(
            summary = "알림 목록 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2491197c19ed8091b349ef0ef4bb0f60?source=copy_link)\n" +
                    "- 본인의 알림 목록을 상태별로 조회합니다.\n\n" +
                    "**Request Parameters:**\n" +
                    "- `status` (String, optional): 알림 상태 (all, unread) - 기본값: all\n" +
                    "- `page` (Integer, optional): 페이지 번호 (1 이상) - 기본값: 1\n" +
                    "- `size` (Integer, optional): 페이지 크기 - 기본값: 20\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 알림 목록 반환\n" +
                    "- 400(BAD_REQUEST): 잘못된 상태 값 또는 페이지 번호\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않은 사용자"
    )
    @GetMapping
    public BaseResponse<Map<String, Object>> list(
            @AuthenticationPrincipal PrincipalDetails pd,
            @RequestParam(defaultValue = "all") String status,   // all | unread
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        Map<String, Object> body = notificationQueryService.getNotifications(status, page, size, pd.getMemberId());
        return BaseResponse.onSuccess(SuccessStatus._OK, body);
    }

    @Operation(
            summary = "알림 읽음 처리 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2491197c19ed80a89ff0c03bc150460f?source=copy_link)\n" +
                    "- 알림 ID에 해당하는 알림을 읽음 처리합니다.\n" +
                    "- 본인의 알림만 읽음 처리 가능합니다.\n\n" +
                    "**Path Variables:**\n" +
                    "- `notificationId` (Long, required): 알림 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 성공 메시지 반환\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않은 사용자\n" +
                    "- 403(FORBIDDEN): 다른 사용자의 알림 접근 시도\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 알림 ID\n" +
                    "- 409(CONFLICT): 이미 읽음 처리된 알림"
    )
    @PostMapping("/{notificationId}/read")
    public BaseResponse<String> markRead(@AuthenticationPrincipal PrincipalDetails pd,
                                         @PathVariable("notificationId") Long notificationId) throws AccessDeniedException {
        notificationCommandService.markRead(notificationId, pd.getMemberId());
        return BaseResponse.onSuccess(SuccessStatus._OK,
                "The notification has been marked as read successfully. id=" + notificationId);
    }

    @Operation(
            summary = "알림 전송 테스트 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2511197c19ed8051bc93d95f0b216543?source=copy_link)\n" +
                    "- 알림 전송 기능을 테스트합니다.\n" +
                    "- 디바이스 토큰을 등록한 이후에 사용 가능합니다.\n\n" +
                    "**Request Body:**\n" +
                    "- `memberId` (Long, required): 대상 멤버 ID\n" +
                    "- `title` (String, required): 알림 제목\n" +
                    "- `body` (String, required): 알림 내용\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 성공 메시지 반환\n" +
                    "- 400(BAD_REQUEST): 필수 필드 누락\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 멤버 ID 또는 디바이스 토큰 없음\n" +
                    "- 500(INTERNAL_SERVER_ERROR): FCM 전송 실패"
    )
    @PostMapping("/queue")
    public BaseResponse<String> queue(@Valid @RequestBody QueueNotificationRequestDTO req) {
        notificationCommandService.queue(req);
        return BaseResponse.onSuccess(SuccessStatus._OK, "Notification delivery succeeded.");
    }

    @Operation(
            summary = "알림 유형별 ON/OFF 토글 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/on-off-2511197c19ed80aeb4eed3c502691361?source=copy_link)\n" +
                    "- 토글 형식으로 유형별 알림을 ON/OFF 합니다.\n" +
                    "- 그룹 토글 기능을 지원합니다.\n\n" +
                    "**Path Variables:**\n" +
                    "- `type` (NotificationType, required): 알림 유형\n" +
                    "  - 개별 유형: CHAT, PARTNER_SUGGESTION, PARTNER_PROPOSAL, ORDER, STAMP\n" +
                    "  - 그룹 유형: PARTNER_ALL (CHAT + ORDER), ADMIN_ALL (CHAT + PARTNER_SUGGESTION + PARTNER_PROPOSAL)\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)와 변경된 알림 설정 상태 반환\n" +
                    "- 400(BAD_REQUEST): 지원하지 않는 알림 유형\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않은 사용자"
    )
    @PutMapping("/{type}")
    public BaseResponse<NotificationSettingsResponseDTO> toggle(
            @AuthenticationPrincipal PrincipalDetails pd,
            @PathVariable("type") NotificationType type
    ) {
        Map<String, Boolean> settings = notificationCommandService.toggle(pd.getMemberId(), type);
        return BaseResponse.onSuccess(SuccessStatus._OK, new NotificationSettingsResponseDTO(settings));
    }

    @Operation(
            summary = "알림 현재 설정 조회 API",
            description = "# [v1.0 (2025-09-02)](https://clumsy-seeder-416.notion.site/2691197c19ed80de9b92d96db3608cdf?source=copy_link)\n" +
                    "- 현재 로그인 사용자의 알림 설정 상태를 반환합니다.\n" +
                    "- 모든 알림 유형에 대한 ON/OFF 상태를 확인할 수 있습니다.\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)와 알림 설정 상태 반환\n" +
                    "- 각 알림 유형별 true/false 값 포함\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않은 사용자"
    )
    @GetMapping("/settings")
    public BaseResponse<NotificationSettingsResponseDTO> getSettings(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        NotificationSettingsResponseDTO res = notificationQueryService.loadSettings(pd.getMemberId());
        return BaseResponse.onSuccess(SuccessStatus._OK, res);
    }

    @Operation(
            summary = "읽지 않은 알림 존재 여부 조회 API",
            description = "# [v1.0 (2025-09-02)](https://clumsy-seeder-416.notion.site/2691197c19ed809a81fec6eb3282ec3a?source=copy_link)\n" +
                    "- 현재 로그인 사용자의 읽지 않은 알림 존재 여부를 반환합니다.\n" +
                    "- 알림 배지 표시 여부를 결정하는 데 사용됩니다.\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)와 존재 여부 반환\n" +
                    "- true: 읽지 않은 알림 있음, false: 모든 알림 읽음\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않은 사용자"
    )
    @GetMapping("/unread-exists")
    public BaseResponse<Boolean> unreadExists(@AuthenticationPrincipal PrincipalDetails pd) {
        boolean exists = notificationQueryService.hasUnread(pd.getMemberId());
        return BaseResponse.onSuccess(SuccessStatus._OK, exists);
    }
}