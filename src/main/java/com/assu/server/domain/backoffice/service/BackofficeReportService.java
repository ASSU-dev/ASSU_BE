package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeReportDTO;

import java.util.List;

public interface BackofficeReportService {
    BackofficeReportDTO.SoftDeleteResponseDTO softDeleteReview(Long reviewId);
    BackofficeReportDTO.SoftDeleteResponseDTO softDeleteSuggestion(Long suggestionId);
    BackofficeReportDTO.RejectReportResponseDTO rejectReport(Long reportId);
    List<BackofficeReportDTO.ReportListItemDTO> getReports(boolean pending, boolean processed, boolean rejected);
}