package com.assu.server.domain.admin.controller;

import com.assu.server.domain.admin.dto.AdminResponseDTO;
import com.assu.server.domain.admin.service.AdminService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "관리자 관련 API", description = "어드민 및 시스템 추천 관련 API")
public class AdminController {

    private final AdminService adminService;

    @Operation(
            summary = "파트너 추천 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2591197c19ed80f5b05cffcfecef9c24)\n" +
                    "- 아직 제휴를 맺지 않은 파트너 중 한 곳을 랜덤으로 추천합니다.\n" +
                    "- **Authentication**: 헤더에 JWT 토큰 필요 (Admin 권한)"
    )
    @GetMapping("/partner-recommend")
    public BaseResponse<AdminResponseDTO.RandomPartnerResponseDTO> randomPartnerRecommend(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, adminService.suggestRandomPartner(pd.getId()));
    }
}