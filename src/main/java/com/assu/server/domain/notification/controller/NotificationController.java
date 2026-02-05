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
                    "- 본인의 알림 목록을 상태별로 조회합니다.\n"+
                    "  - status: Request Param, String, [all/unread]\n" +
                    "  - page: Request Param, Integer, 1 이상\n" +
                    "  - size: Request Param, Integer, default = 20"
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
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2491197c19ed80a89ff0c03bc150460f?source=copy_link) \n" +
                    "- 알림 아이디에 해당하는 알림을 읽음 처리합니다.\n"+
                    "  - notification-id: Path Variable, Long\n"
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
                    "- deviceToken을 등록한 이후에 사용 가능합니다."
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
                    "  - type: Path Variable, NotificationType \n" +
                    "  - 지원 값: [CHAT / PARTNER_SUGGESTION / PARTNER_PROPOSAL / ORDER / PARTNER_ALL / ADMIN_ALL]\n" +
                    "  - PARTNER_ALL: CHAT + ORDER를 함께 토글\n" +
                    "  - ADMIN_ALL: CHAT + PARTNER_SUGGESTION + PARTNER_PROPOSAL을 함께 토글"
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
                    "- 현재 로그인 사용자의 알림 설정 상태를 반환합니다.\n"
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
                    "- 결과: true | false"
    )
    @GetMapping("/unread-exists")
    public BaseResponse<Boolean> unreadExists(@AuthenticationPrincipal PrincipalDetails pd) {
        boolean exists = notificationQueryService.hasUnread(pd.getMemberId());
        return BaseResponse.onSuccess(SuccessStatus._OK, exists);
    }
}