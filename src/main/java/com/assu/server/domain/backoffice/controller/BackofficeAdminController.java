package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.dto.BackofficeDocumentUrlResponseDTO;
import com.assu.server.domain.backoffice.service.BackofficeMemberService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/admins")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeAdminController {

    private final BackofficeMemberService backofficeMemberService;

    @Operation(
            summary = "인감 이미지 조회 API",
            description = "# [v1.0 (2026-07-03)]\n" +
                    "- Admin 회원의 인감 이미지 S3 presigned URL을 반환합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `memberId` (Long, required): Admin 회원 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 presigned URL 반환 (약 10분 유효)\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): Admin이 아니거나 인감 이미지 없음"
    )
    @GetMapping("/{memberId}/sign-image")
    public BaseResponse<BackofficeDocumentUrlResponseDTO> getSignImageUrl(
            @Parameter(description = "Admin 회원 ID") @PathVariable Long memberId
    ) {
        return BaseResponse.onSuccess(
                SuccessStatus._OK,
                backofficeMemberService.getAdminSignImageUrl(memberId)
        );
    }
}
