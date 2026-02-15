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

@RestController
@RequestMapping("/partner")
@RequiredArgsConstructor
@Tag(name = "파트너 관련 API", description = "파트너 전용 기능 및 추천 관련 API")
public class PartnerController {

    private final PartnerService partnerService;

    @Operation(
            summary = "어드민 추천 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2591197c19ed80368a9edf1f6e92ea38)\n" +
                    "- 아직 제휴를 맺지 않은 어드민(학교/단체) 중 두 곳을 랜덤으로 조회합니다.\n" +
                    "- **Authentication**: 헤더에 JWT 토큰 필요 (Partner 권한)"
    )
    @GetMapping("/admin-recommend")
    public BaseResponse<PartnerResponseDTO.RandomAdminResponseDTO> randomAdminRecommend(
            @AuthenticationPrincipal PrincipalDetails pd
    ){
        return BaseResponse.onSuccess(SuccessStatus._OK, partnerService.getRandomAdmin(pd.getId()));
    }
}