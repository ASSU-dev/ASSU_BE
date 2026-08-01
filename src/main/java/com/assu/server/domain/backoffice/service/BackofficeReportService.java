package com.assu.server.domain.backoffice.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.backoffice.dto.BackofficeReportDTO;
import com.assu.server.domain.backoffice.dto.BackofficeReportResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeReportStatusUpdateRequestDTO;

public interface BackofficeReportService {
    Page<BackofficeReportResponseDTO> getReports(Pageable pageable);
    BackofficeReportResponseDTO getReportDetail(Long reportId);
    BackofficeReportResponseDTO updateReportStatus(Long reportId, BackofficeReportStatusUpdateRequestDTO req);

    BackofficeReportDTO.SoftDeleteResponseDTO softDeleteReview(Long reviewId);
    BackofficeReportDTO.SoftDeleteResponseDTO softDeleteSuggestion(Long suggestionId);
    BackofficeReportDTO.RejectReportResponseDTO rejectReport(Long reportId);
    List<BackofficeReportDTO.ReportListItemDTO> getReports(boolean pending, boolean processed, boolean rejected);
}