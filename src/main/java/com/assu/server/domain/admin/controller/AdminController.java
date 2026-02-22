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

@Tag(name = "Admin", description = "관리자 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    @Operation(
            summary = "제휴업체 추천 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/2591197c19ed80f5b05cffcfecef9c24?source=copy_link)\n" +
                    "- 현재 로그인 한 관리자와 제휴하지 않은 제휴업체 중 한 곳을 랜덤으로 조회합니다.\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 `RandomPartnerResponse` 객체 반환\n" +
                    "  - `partnerId` (Long): 제휴업체 ID\n" +
                    "  - `partnerAddress` (String): 제휴업체 주소\n" +
                    "  - `partnerDetailAddress` (String): 제휴업체 상세주소\n" +
                    "  - `partnerName` (String): 제휴업체 상호명\n" +
                    "  - `partnerUrl` (String): 제휴업체 카카오맵 URL\n" +
                    "  - `partnerPhone` (String): 제휴업체 전화번호\n")
    @GetMapping("/partner-recommend")
    public BaseResponse<AdminResponseDTO> randomPartnerRecommend(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, adminService.suggestRandomPartner(pd.getId()));
    }
}
