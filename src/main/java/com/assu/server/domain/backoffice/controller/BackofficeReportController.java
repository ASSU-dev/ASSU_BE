package com.assu.server.domain.backoffice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
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
@RequestMapping("/backoffice/reports")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeReportController {

    private final BackofficeReportService backofficeReportService;

    @BackofficeAudited(action = "REPORT_ALL_READ")
    @Operation(summary = "모든 신고 목록 조회 API (백오피스용)", description = "시스템에 접수된 모든 신고 목록을 페이징 조회합니다.")
    @GetMapping
    public BaseResponse<Page<BackofficeReportResponseDTO>> getReports(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeReportService.getReports(pageable));
    }

    @BackofficeAudited(action = "REPORT_DETAIL_READ", targetId = "#reportId")
    @Operation(summary = "신고 상세 조회 API (백오피스용)", description = "특정 신고 ID 기준 상세 내용을 조회합니다.")
    @GetMapping("/{reportId}")
    public BaseResponse<BackofficeReportResponseDTO> getReportDetail(
            @PathVariable @Parameter(description = "신고 ID", required = true) Long reportId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeReportService.getReportDetail(reportId));
    }

    @BackofficeAudited(action = "REPORT_STATUS_UPDATE", targetId = "#reportId")
    @Operation(summary = "신고 상태 변경 및 기본 처리 API (백오피스용)", description = "신고의 처리 상태를 변경하고 관련 메모를 남깁니다.")
    @PatchMapping("/{reportId}/status")
    public BaseResponse<BackofficeReportResponseDTO> updateReportStatus(
            @PathVariable @Parameter(description = "신고 ID", required = true) Long reportId,
            @RequestBody @Valid BackofficeReportStatusUpdateRequestDTO req
    ) {
        BackofficeReportResponseDTO response = backofficeReportService.updateReportStatus(reportId, req);
        return BaseResponse.onSuccess(SuccessStatus._OK, response);
    }
}