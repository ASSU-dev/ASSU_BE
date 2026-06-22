package com.assu.server.domain.auth.controller;

import com.assu.server.domain.auth.dto.backoffice.BackofficeLoginResponseDTO;
import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.dto.login.RefreshResponseDTO;
import com.assu.server.domain.auth.service.BackofficeAuthService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backoffice Auth", description = "백오피스 전용 인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/backoffice")
public class BackofficeAuthController {

    private final BackofficeAuthService backofficeAuthService;

    @Operation(
            summary = "백오피스 로그인 API",
            description = "# [v1.0 (2026-06-23)]\n" +
                    "- `application/json`으로 호출합니다.\n" +
                    "- 바디: `CommonLoginRequestDTO(email, password)`.\n" +
                    "- `BACKOFFICE` 역할 계정만 로그인할 수 있습니다.\n" +
                    "- 처리: 자격 증명 검증 후 `aud=backoffice` Access/Refresh 토큰 발급 및 저장.\n" +
                    "- 성공 시 200(OK)과 토큰(accessToken/refreshToken), 기본 정보 반환.\n" +
                    "\n**Request Body:**\n" +
                    "  - `CommonLoginRequestDTO` 객체 (JSON, required): 로그인 정보\n" +
                    "  - `email` (String, required): 이메일 주소\n" +
                    "  - `password` (String, required): 비밀번호\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 `BackofficeLoginResponseDTO` 객체 반환\n" +
                    "  - `memberId` (Long): 회원 ID\n" +
                    "  - `role` (UserRole): BACKOFFICE\n" +
                    "  - `status` (ActivationStatus): 회원 상태\n" +
                    "  - `tokens` (Object): JWT 토큰 정보 (accessToken, refreshToken)\n" +
                    "  - `basicInfo` (UserBasicInfo): 운영자 이름 등 기본 정보\n" +
                    "  - 403(FORBIDDEN): BACKOFFICE 역할이 아닌 계정\n" +
                    "  - 401(UNAUTHORIZED): 이메일 또는 비밀번호 불일치"
    )
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<BackofficeLoginResponseDTO> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "백오피스 로그인 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CommonLoginRequestDTO.class)
                    )
            )
            @RequestBody
            @Valid
            CommonLoginRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeAuthService.login(request));
    }

    @Operation(
            summary = "백오피스 Access Token 갱신 API",
            description = "# [v1.0 (2026-06-23)]\n" +
                    "- 헤더로 호출합니다.\n" +
                    "- 헤더: `RefreshToken: <refreshToken>`.\n" +
                    "- `aud=backoffice` Refresh Token만 갱신할 수 있습니다.\n" +
                    "- 처리: Refresh 검증/회전 후 신규 Access/Refresh 발급 및 저장.\n" +
                    "- 성공 시 200(OK)과 신규 토큰 반환.\n" +
                    "\n**Headers:**\n" +
                    "  - `RefreshToken` (String, required): 백오피스 리프레시 토큰\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 `RefreshResponseDTO` 객체 반환\n" +
                    "  - `memberId` (Long): 회원 ID\n" +
                    "  - `newAccess` (String): 새로운 액세스 토큰\n" +
                    "  - `newRefresh` (String): 새로운 리프레시 토큰\n" +
                    "  - 401(UNAUTHORIZED): audience 불일치 또는 유효하지 않은 Refresh Token"
    )
    @PostMapping("/tokens/refresh")
    public BaseResponse<RefreshResponseDTO> refresh(
            @Parameter(
                    name = "RefreshToken",
                    description = "Backoffice Refresh Token",
                    required = true,
                    in = ParameterIn.HEADER,
                    schema = @Schema(type = "string")
            )
            @RequestHeader("RefreshToken")
            String refreshToken
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeAuthService.refresh(refreshToken));
    }
}
