package com.assu.server.domain.backoffice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.backoffice.dto.BackofficeReportResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeReportStatusUpdateRequestDTO;

public interface BackofficeReportService {
    Page<BackofficeReportResponseDTO> getReports(Pageable pageable);
    BackofficeReportResponseDTO getReportDetail(Long reportId);
    BackofficeReportResponseDTO updateReportStatus(Long reportId, BackofficeReportStatusUpdateRequestDTO req);
}