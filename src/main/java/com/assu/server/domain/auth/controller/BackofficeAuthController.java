package com.assu.server.domain.auth.controller;

import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.dto.login.RefreshResponseDTO;
import com.assu.server.domain.auth.dto.backoffice.BackofficeLoginResponseDTO;
import com.assu.server.domain.auth.service.BackofficeAuthService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "백오피스 로그인 API", description = "BACKOFFICE 역할 계정 전용 로그인. aud=backoffice JWT 발급.")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<BackofficeLoginResponseDTO> login(@RequestBody @Valid CommonLoginRequestDTO request) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeAuthService.login(request));
    }

    @Operation(summary = "백오피스 Access Token 갱신 API", description = "aud=backoffice refresh token 전용.")
    @PostMapping("/tokens/refresh")
    public BaseResponse<RefreshResponseDTO> refresh(@RequestHeader("RefreshToken") String refreshToken) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeAuthService.refresh(refreshToken));
    }
}
