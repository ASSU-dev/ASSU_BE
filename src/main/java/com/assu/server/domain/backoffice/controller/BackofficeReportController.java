package com.assu.server.domain.backoffice.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficeReportDTO;
import com.assu.server.domain.backoffice.dto.BackofficeReportResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeReportStatusUpdateRequestDTO;
import com.assu.server.domain.backoffice.service.BackofficeReportService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Backoffice Report", description = "백오피스 신고 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeReportController {

    private final BackofficeReportService backofficeReportService;

    @BackofficeAudited(action = "REPORT_ALL_READ")
    @Operation(summary = "모든 신고 목록 조회 API (백오피스용)", description = "시스템에 접수된 모든 신고 목록을 페이징 조회합니다.")
    @GetMapping("/reports")
    public BaseResponse<Page<BackofficeReportResponseDTO>> getReports(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeReportService.getReports(pageable));
    }

    @BackofficeAudited(action = "REPORT_DETAIL_READ", targetId = "#reportId")
    @Operation(summary = "신고 상세 조회 API (백오피스용)", description = "특정 신고 ID 기준 상세 내용을 조회합니다.")
    @GetMapping("/reports/{reportId}")
    public BaseResponse<BackofficeReportResponseDTO> getReportDetail(
            @PathVariable @Parameter(description = "신고 ID", required = true) Long reportId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeReportService.getReportDetail(reportId));
    }

    @BackofficeAudited(action = "REPORT_STATUS_UPDATE", targetId = "#reportId")
    @Operation(summary = "신고 상태 변경 및 기본 처리 API (백오피스용)", description = "신고의 처리 상태를 변경합니다.")
    @PatchMapping("/reports/{reportId}/status")
    public BaseResponse<BackofficeReportResponseDTO> updateReportStatus(
            @PathVariable @Parameter(description = "신고 ID", required = true) Long reportId,
            @RequestBody @Valid BackofficeReportStatusUpdateRequestDTO req
    ) {
        BackofficeReportResponseDTO response = backofficeReportService.updateReportStatus(reportId, req);
        return BaseResponse.onSuccess(SuccessStatus._OK, response);
    }

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
            summary = "신고 필터 목록 조회 API",
            description = "- `pending`, `processed`, `rejected` 파라미터로 조회할 신고 상태를 지정합니다.\n" +
                    "- 모두 false(기본값)이면 전체 신고를 반환합니다.\n" +
                    "- 여러 파라미터를 true로 지정하면 해당 상태들을 모두 포함합니다.\n\n" +
                    "**Query Params:**\n" +
                    "- `pending` (boolean, default false): PENDING 신고 포함\n" +
                    "- `processed` (boolean, default false): PROCESSED 신고 포함\n" +
                    "- `rejected` (boolean, default false): REJECTED 신고 포함"
    )
    @GetMapping(value = "/reports", params = {"pending", "processed", "rejected"})
    public BaseResponse<List<BackofficeReportDTO.ReportListItemDTO>> getReportsByFilter(
            @RequestParam(defaultValue = "false") boolean pending,
            @RequestParam(defaultValue = "false") boolean processed,
            @RequestParam(defaultValue = "false") boolean rejected
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK,
                backofficeReportService.getReports(pending, processed, rejected));
    }
}