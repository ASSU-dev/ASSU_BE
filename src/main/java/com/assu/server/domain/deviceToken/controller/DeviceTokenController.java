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
            summary = "Device Token 등록 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/24e1197c19ed8092864ac5a1ddc88d07?source=copy_link)\n" +
                    "- Device Token을 등록하고 등록된 Token의 ID를 반환합니다.\n" +
                    "  - 'token': Request Param, String\n"
    )
    @PostMapping
    public BaseResponse<Long> register(@AuthenticationPrincipal PrincipalDetails pd,
                                       @RequestParam String token) {
        Long tokenId = service.register(token, pd.getId());
        return BaseResponse.onSuccess(SuccessStatus._OK, tokenId);
    }
    @Operation(
            summary = "Device Token 등록 해제 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/24e1197c19ed80b8b26be9e01d24c929?source=copy_link)\n" +
                    "- 로그아웃/탈퇴 시 호출해 device Token 등록을 해제합니다. 자신의 토큰만 해제가 가능합니다.\n"+
                    "  - 'token-id': Path Variable, Long\n"
    )
    @DeleteMapping("/{token-id}")
    public BaseResponse<String> unregister(@AuthenticationPrincipal PrincipalDetails pd,
                                           @PathVariable("token-id") Long tokenId) {
        service.unregister(tokenId, pd.getId());
        return BaseResponse.onSuccess(
                SuccessStatus._OK,
                "Device token unregistered successfully. tokenId=" + tokenId
        );
    }
}