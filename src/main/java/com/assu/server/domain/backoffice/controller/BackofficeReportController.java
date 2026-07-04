package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficeReportDTO;
import com.assu.server.domain.backoffice.service.BackofficeReportService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeReportController {

    private final BackofficeReportService backofficeReportService;

    @BackofficeAudited(action = "REVIEW_DELETE", targetId = "#reviewId")
    @Operation(
            summary = "리뷰 소프트 삭제 API",
            description = "- 리뷰를 삭제 처리합니다 (status → DELETED).\n" +
                    "- 삭제된 리뷰는 일반 조회 API에서 노출되지 않습니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `reviewId` (Long, required): 삭제할 리뷰 ID"
    )
    @DeleteMapping("/reviews/{reviewId}")
    public BaseResponse<BackofficeReportDTO.SoftDeleteResponseDTO> deleteReview(
            @PathVariable Long reviewId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK,
                backofficeReportService.softDeleteReview(reviewId));
    }

    @BackofficeAudited(action = "SUGGESTION_DELETE", targetId = "#suggestionId")
    @Operation(
            summary = "건의글 소프트 삭제 API",
            description = "- 건의글을 삭제 처리합니다 (status → DELETED).\n" +
                    "- 삭제된 건의글은 일반 조회 API에서 노출되지 않습니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `suggestionId` (Long, required): 삭제할 건의글 ID"
    )
    @DeleteMapping("/suggestions/{suggestionId}")
    public BaseResponse<BackofficeReportDTO.SoftDeleteResponseDTO> deleteSuggestion(
            @PathVariable Long suggestionId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK,
                backofficeReportService.softDeleteSuggestion(suggestionId));
    }

    @BackofficeAudited(action = "REPORT_REJECT", targetId = "#reportId")
    @Operation(
            summary = "신고 기각 API",
            description = "- 신고를 기각 처리합니다 (ReportStatus → REJECTED).\n" +
                    "- 기각 시 신고 대상 콘텐츠의 ReportedStatus가 NORMAL로 복구됩니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `reportId` (Long, required): 기각할 신고 ID"
    )
    @PatchMapping("/reports/{reportId}/reject")
    public BaseResponse<BackofficeReportDTO.RejectReportResponseDTO> rejectReport(
            @PathVariable Long reportId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK,
                backofficeReportService.rejectReport(reportId));
    }

    @Operation(
            summary = "신고 목록 조회 API",
            description = "- `pending`, `processed`, `rejected` 파라미터로 조회할 신고 상태를 지정합니다.\n" +
                    "- 모두 false(기본값)이면 전체 신고를 반환합니다.\n" +
                    "- 여러 파라미터를 true로 지정하면 해당 상태들을 모두 포함합니다.\n\n" +
                    "**Query Params:**\n" +
                    "- `pending` (boolean, default false): PENDING 신고 포함\n" +
                    "- `processed` (boolean, default false): PROCESSED 신고 포함\n" +
                    "- `rejected` (boolean, default false): REJECTED 신고 포함"
    )
    @GetMapping("/reports")
    public BaseResponse<List<BackofficeReportDTO.ReportListItemDTO>> getReports(
            @RequestParam(defaultValue = "false") boolean pending,
            @RequestParam(defaultValue = "false") boolean processed,
            @RequestParam(defaultValue = "false") boolean rejected
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK,
                backofficeReportService.getReports(pending, processed, rejected));
    }
}