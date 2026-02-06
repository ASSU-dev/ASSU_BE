package com.assu.server.domain.partner.controller;

import com.assu.server.domain.partner.dto.PartnerResponseDTO;
import com.assu.server.domain.partner.service.PartnerService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Partner", description = "제휴업체 API")
@RestController
@RequestMapping("/partner")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    @Operation(
            summary = "관리자 추천 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/2591197c19ed80368a9edf1f6e92ea38)\n" +
                    "- 현재 로그인 한 제휴업체와 제휴하지 않은 관리자 중 최대 두 곳을 랜덤으로 조회합니다.\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 `RandomAdminResponse` 객체(최대 두 개) 반환\n" +
                    "  - `adminId` (Long): 관리자 ID\n" +
                    "  - `adminAddress` (String): 관리자 주소\n" +
                    "  - `adminDetailAddress` (String): 관리자 상세주소\n" +
                    "  - `adminName` (String): 관리자 상호명\n" +
                    "  - `adminUrl` (String): 관리자 카카오맵 URL\n" +
                    "  - `adminPhone` (String): 관리자 전화번호\n")
    @GetMapping("/admin-recommend")
    public BaseResponse<PartnerResponseDTO.RandomAdminResponseDTO> randomAdminRecommend(
            @AuthenticationPrincipal PrincipalDetails pd
            ){
        return BaseResponse.onSuccess(SuccessStatus._OK, partnerService.getRandomAdmin(pd.getId()));
    }
}
