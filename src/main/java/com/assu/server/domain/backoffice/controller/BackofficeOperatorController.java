package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficeOperatorCreateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeOperatorResponseDTO;
import com.assu.server.domain.backoffice.service.BackofficeOperatorService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/operators")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeOperatorController {

    private final BackofficeOperatorService backofficeOperatorService;

    @BackofficeAudited(action = "OPERATOR_CREATE")
    @Operation(
            summary = "백오피스 운영자 생성 API",
            description = "# [v1.0 (2026-06-23)]\n" +
                    "- 새로운 `BACKOFFICE` 운영자 계정을 생성합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Request Body:**\n" +
                    "- `email` (String, required): 로그인 이메일\n" +
                    "- `password` (String, required): 비밀번호 (8~72자)\n" +
                    "- `name` (String, required): 운영자 이름\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 `BackofficeOperatorResponseDTO` 반환\n" +
                    "  - `memberId` (Long): 회원 ID\n" +
                    "  - `email` (String): 이메일\n" +
                    "  - `name` (String): 운영자 이름\n" +
                    "  - `status` (ActivationStatus): 활성 상태\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 409(CONFLICT): 이미 사용 중인 이메일"
    )
    @PostMapping
    public BaseResponse<BackofficeOperatorResponseDTO> createOperator(
            @RequestBody @Valid BackofficeOperatorCreateRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeOperatorService.createOperator(request));
    }

    @Operation(
            summary = "백오피스 운영자 목록 조회 API",
            description = "# [v1.0 (2026-06-23)]\n" +
                    "- 등록된 `BACKOFFICE` 운영자 목록을 조회합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 `BackofficeOperatorResponseDTO` 목록 반환\n" +
                    "  - `memberId` (Long): 회원 ID\n" +
                    "  - `email` (String): 이메일\n" +
                    "  - `name` (String): 운영자 이름\n" +
                    "  - `status` (ActivationStatus): 활성 상태\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음"
    )
    @GetMapping
    public BaseResponse<List<BackofficeOperatorResponseDTO>> listOperators() {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeOperatorService.listOperators());
    }
}
