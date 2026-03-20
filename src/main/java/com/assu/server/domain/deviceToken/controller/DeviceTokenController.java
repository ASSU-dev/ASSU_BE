package com.assu.server.domain.deviceToken.controller;

import com.assu.server.domain.deviceToken.service.DeviceTokenService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Device Token", description = "디바이스 토큰 등록/해제 API")
@RestController
@RequestMapping("/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService service;

    @Operation(
            summary = "디바이스 토큰 등록 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/24e1197c19ed8092864ac5a1ddc88d07?source=copy_link)\n" +
                    "- 디바이스 토큰을 등록하고 등록된 토큰의 ID를 반환합니다.\n" +
                    "- 푸시 알림 수신을 위해 필수로 등록해야 합니다.\n\n" +
                    "**Request Parameters:**\n" +
                    "- `token` (String, required): FCM 디바이스 토큰\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 등록된 토큰 ID 반환\n" +
                    "- 400(BAD_REQUEST): 빈 토큰 또는 잘못된 토큰 형식\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않은 사용자\n" +
                    "- 409(CONFLICT): 이미 등록된 토큰"
    )
    @PostMapping
    public BaseResponse<Long> register(@AuthenticationPrincipal PrincipalDetails pd,
                                       @RequestParam String token) {
        Long tokenId = service.register(token, pd.getId());
        return BaseResponse.onSuccess(SuccessStatus._OK, tokenId);
    }
    @Operation(
            summary = "디바이스 토큰 등록 해제 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/24e1197c19ed80b8b26be9e01d24c929?source=copy_link)\n" +
                    "- 로그아웃/탈퇴 시 호출해 디바이스 토큰 등록을 해제합니다.\n" +
                    "- 자신의 토큰만 해제가 가능합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `tokenId` (Long, required): 해제할 디바이스 토큰 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 성공 메시지 반환\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않은 사용자\n" +
                    "- 403(FORBIDDEN): 다른 사용자의 토큰 해제 시도\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 토큰 ID"
    )
    @DeleteMapping("/{tokenId}")
    public BaseResponse<String> unregister(@AuthenticationPrincipal PrincipalDetails pd,
                                           @PathVariable("tokenId") Long tokenId) {
        service.unregister(tokenId, pd.getId());
        return BaseResponse.onSuccess(
                SuccessStatus._OK,
                "Device token unregistered successfully. tokenId=" + tokenId
        );
    }
}