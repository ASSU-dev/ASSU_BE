package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficeDevSyncResponseDTO;
import com.assu.server.domain.backoffice.service.BackofficeDevSyncService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.apiPayload.code.status.SwaggerErrorCodes;
import com.assu.server.global.exception.annotation.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/dev-sync")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeDevSyncController {

    private final BackofficeDevSyncService backofficeDevSyncService;

    @BackofficeAudited(action = "DEV_SYNC")
    @ApiErrorCodeExamples(SwaggerErrorCodes.BACKOFFICE_DEV_SYNC)
    @Operation(
            summary = "Prod DB → Dev DB 동기화 API",
            description = "# [v1.0 (2026-10-04)]\n" +
                    "- 현재 서버의 DB 스키마를 `dev-sync.target-schema` 스키마로 통째로 복사합니다.\n" +
                    "- 대상 스키마의 기존 테이블은 모두 삭제되고, 원본과 동일한 구조/데이터로 다시 생성됩니다.\n" +
                    "- 복사 후 대상 스키마의 `device_token`, `notification_outbox`는 비웁니다.\n" +
                    "- **완료 후 dev 서버를 재시작해야** develop에만 있는 컬럼이 `ddl-auto`로 다시 생성됩니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 `BackofficeDevSyncResponseDTO` 반환\n" +
                    "  - `sourceSchema` (String): 복사 원본 스키마\n" +
                    "  - `targetSchema` (String): 복사 대상 스키마\n" +
                    "  - `copiedTables` (List<String>): 복사한 테이블 목록\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 500(INTERNAL_SERVER_ERROR): 대상 스키마 미설정 또는 원본과 동일"
    )
    @PostMapping
    public BaseResponse<BackofficeDevSyncResponseDTO> syncToDev() {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeDevSyncService.syncToDev());
    }
}
