package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.suggestion.dto.BackofficeSuggestionResponseDTO;
import com.assu.server.domain.suggestion.entity.enums.SuggestionCheckFilter;
import com.assu.server.domain.suggestion.service.BackofficeSuggestionService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.apiPayload.code.status.SwaggerErrorCodes;
import com.assu.server.global.exception.annotation.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/suggestions")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeSuggestionController {

    private final BackofficeSuggestionService backofficeSuggestionService;

    @ApiErrorCodeExamples(SwaggerErrorCodes.BACKOFFICE_SUGGESTION_LIST)
    @Operation(
            summary = "운영자 제휴 건의 목록 조회 API",
            description = "# [v1.0 (2026-11-21)]\n" +
                    "- 모든 학생의 제휴 건의를 최신순으로 페이징 조회합니다.\n" +
                    "- 신고·삭제된 건의도 포함되며 `reportedStatus`로 구분합니다.\n" +
                    "- `checkFilter`로 운영자 확인 여부를 필터링합니다 (ALL / UNCHECKED / CHECKED).\n" +
                    "- `keyword`가 있으면 가게명 또는 건의 내용에서 검색합니다.\n\n" +
                    "**Query Parameters:**\n" +
                    "- `checkFilter` (default: ALL): 확인 여부 필터\n" +
                    "- `keyword` (optional): 가게명 또는 건의 내용 검색어\n" +
                    "- `page` (default: 1): 페이지 번호\n" +
                    "- `size` (default: 20): 페이지 크기\n\n" +
                    "**Response:**\n" +
                    "- 200(OK): 건의 목록 페이지 반환\n" +
                    "- 401(UNAUTHORIZED): 인증 실패 또는 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음"
    )
    @GetMapping
    public BaseResponse<PageResponseDTO<BackofficeSuggestionResponseDTO>> listSuggestions(
            @Parameter(description = "확인 여부 필터 (ALL / UNCHECKED / CHECKED)", example = "ALL")
            @RequestParam(defaultValue = "ALL") SuggestionCheckFilter checkFilter,
            @Parameter(description = "가게명 또는 건의 내용 검색어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (1 이상)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~200)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK,
                backofficeSuggestionService.getSuggestions(checkFilter, keyword, page, size));
    }

    @BackofficeAudited(action = "SUGGESTION_CHECK", targetId = "#suggestionId")
    @ApiErrorCodeExamples(SwaggerErrorCodes.BACKOFFICE_SUGGESTION_CHECK)
    @Operation(
            summary = "운영자 제휴 건의 확인 처리 API",
            description = "# [v1.0 (2026-11-21)]\n" +
                    "- 제휴 건의를 운영자 확인 상태로 변경합니다.\n" +
                    "- 이미 확인된 건의는 최초 확인 일시를 유지하고 그대로 반환합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `suggestionId` (Long, required): 건의 ID\n\n" +
                    "**Response:**\n" +
                    "- 200(OK): 확인 처리된 건의 반환\n" +
                    "- 401(UNAUTHORIZED): 인증 실패 또는 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 건의 ID"
    )
    @PatchMapping("/{suggestionId}/check")
    public BaseResponse<BackofficeSuggestionResponseDTO> checkSuggestion(
            @PathVariable("suggestionId") Long suggestionId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeSuggestionService.check(suggestionId));
    }
}
