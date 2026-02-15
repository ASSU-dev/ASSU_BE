package com.assu.server.domain.appreview.controller;

import com.assu.server.domain.appreview.dto.AppReviewRequestDTO;
import com.assu.server.domain.appreview.service.AppReviewService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "App Review", description = "앱 리뷰 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/app-reviews")
public class AppReviewController {

    private final AppReviewService appReviewService;

    @Operation(
            summary = "앱 리뷰 작성 API",
            description = "# [v1.0 (2026-02-09)](https://clumsy-seeder-416.notion.site/3021197c19ed80cf9ce3e7b8d7401fad)\n" +
                    "- `application/json`로 호출합니다.\n" +
                    "- 바디: `AppReviewRequestDTO(rate, content)`.\n" +
                    "- 처리: 자격 증명 검증 후 앱 리뷰 생성 및 저장.\n" +
                    "- 성공 시 200(OK) 반환.\n" +
                    "\n**Request Body:**\n" +
                    "  - `rate` (Integer, required): 별점 1~5\n" +
                    "  - `content` (String, required): 후기 내용\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 성공 메시지 반환"
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<Void> createAppReview(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "앱 리뷰 작성 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AppReviewRequestDTO.class)
                    )
            )
            @RequestBody
            @Valid
            AppReviewRequestDTO request,
            @AuthenticationPrincipal
            PrincipalDetails pd
    ) {
        appReviewService.create(request, pd.getId());
        return BaseResponse.onSuccess(SuccessStatus._OK, null);
    }
}
