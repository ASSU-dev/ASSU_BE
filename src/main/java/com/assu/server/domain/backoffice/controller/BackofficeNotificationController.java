package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficeOutboxResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficePushLogResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficePushSendRequestDTO;
import com.assu.server.domain.backoffice.service.BackofficeNotificationService;
import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/notifications")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeNotificationController {

    private final BackofficeNotificationService backofficeNotificationService;

    @BackofficeAudited(action = "PUSH_SEND", targetId = "#request.receiverId()")
    @Operation(
            summary = "운영자 수동 푸시 알림 전송 API",
            description = "# [v2.0 (2026-07-04)]\n" +
                    "- 그룹 또는 특정 사용자에게 자유 메시지 푸시 알림을 전송합니다.\n" +
                    "- `targetType`에 따라 수신자 범위가 결정됩니다.\n\n" +
                    "**Request Body:**\n" +
                    "- `targetType` (required): ALL / STUDENT / UNION / PARTNER / INDIVIDUAL\n" +
                    "- `receiverId` (INDIVIDUAL일 때만 필수): 수신자 멤버 ID\n" +
                    "- `title` (required): 푸시 제목\n" +
                    "- `body` (required): 푸시 본문\n" +
                    "- `deepLink` (optional): 딥링크 URL\n\n" +
                    "**Response:**\n" +
                    "- 200(OK): 발송 완료\n" +
                    "- 400(BAD_REQUEST): 필수 파라미터 누락\n" +
                    "- 401(UNAUTHORIZED): 인증 실패 또는 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 수신자 멤버 ID"
    )
    @PostMapping("/push")
    public BaseResponse<String> sendPush(
            @RequestBody @Valid BackofficePushSendRequestDTO request,
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        backofficeNotificationService.sendPush(request, principal.getMemberId());
        return BaseResponse.onSuccess(SuccessStatus._OK, "Push notifications sent successfully. targetType=" + request.targetType());
    }

    @Operation(
            summary = "푸시 발송 이력 조회 API",
            description = "# [v2.0 (2026-07-04)]\n" +
                    "- 백오피스에서 발송한 푸시 알림 이력을 페이징으로 조회합니다.\n" +
                    "- `keyword`가 있으면 제목/본문에서 검색합니다.\n\n" +
                    "**Query Parameters:**\n" +
                    "- `keyword` (optional): 제목 또는 본문 검색어\n" +
                    "- `page` (default: 1): 페이지 번호\n" +
                    "- `size` (default: 20): 페이지 크기\n\n" +
                    "**Response:**\n" +
                    "- 200(OK): 푸시 이력 목록 페이지 반환\n" +
                    "- 401(UNAUTHORIZED): 인증 실패 또는 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음"
    )
    @GetMapping
    public BaseResponse<PageResponseDTO<BackofficePushLogResponseDTO>> getPushLogs(
            @Parameter(description = "제목 또는 본문 검색어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (1 이상)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~200)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeNotificationService.getPushLogs(keyword, page, size));
    }

    @Operation(
            summary = "전송 실패 알림 목록 조회 API",
            description = "# [v1.0 (2026-06-25)]\n" +
                    "- 전송 상태가 FAILED인 알림 Outbox 목록을 페이징으로 조회합니다.\n" +
                    "- 자동 재시도 한도(3회)를 초과하여 실패 처리된 알림을 확인할 때 사용합니다.\n\n" +
                    "**Query Parameters:**\n" +
                    "- `page` (default: 1): 페이지 번호\n" +
                    "- `size` (default: 20): 페이지 크기\n\n" +
                    "**Response:**\n" +
                    "- 200(OK): 실패 알림 목록 페이지 반환\n" +
                    "- 401(UNAUTHORIZED): 인증 실패 또는 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음"
    )
    @GetMapping("/outbox/failed")
    public BaseResponse<PageResponseDTO<BackofficeOutboxResponseDTO>> getFailedOutboxes(
            @Parameter(description = "페이지 번호 (1 이상)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~200)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeNotificationService.getFailedOutboxes(page, size));
    }

    @BackofficeAudited(action = "PUSH_RETRY", targetId = "#outboxId")
    @Operation(
            summary = "실패 알림 수동 재전송 API",
            description = "# [v1.0 (2026-06-25)]\n" +
                    "- FAILED 상태의 특정 알림 Outbox를 수동으로 재전송합니다.\n" +
                    "- 자동 재시도 횟수(retryCount)와 무관하게 재전송 가능합니다.\n" +
                    "- 재전송 시 Outbox 상태가 PENDING으로 초기화되고 전송 파이프라인에 재등록됩니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `outboxId` (Long, required): 재전송할 Outbox ID\n\n" +
                    "**Response:**\n" +
                    "- 200(OK): 재전송 큐 등록 성공\n" +
                    "- 400(BAD_REQUEST): FAILED 상태가 아닌 Outbox\n" +
                    "- 401(UNAUTHORIZED): 인증 실패 또는 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 Outbox ID"
    )
    @PostMapping("/outbox/{outboxId}/retry")
    public BaseResponse<String> retryOutbox(
            @PathVariable("outboxId") Long outboxId
    ) {
        backofficeNotificationService.retryOutbox(outboxId);
        return BaseResponse.onSuccess(SuccessStatus._OK, "Outbox retry queued successfully. outboxId=" + outboxId);
    }
}