package com.assu.server.domain.backoffice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.assu.server.domain.backoffice.dto.BackofficeReportResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeReportStatusUpdateRequestDTO;
import com.assu.server.domain.report.entity.Report;
import com.assu.server.domain.report.entity.enums.ReportStatus;
import com.assu.server.domain.report.repository.ReportRepository;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BackofficeReportServiceImpl implements BackofficeReportService {

    private final ReportRepository reportRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<BackofficeReportResponseDTO> getReports(Pageable pageable) {
        Page<Report> reports = reportRepository.findAll(pageable);
        return reports.map(BackofficeReportResponseDTO::from);
    }

    @Override
    @Transactional(readOnly = true)
    public BackofficeReportResponseDTO getReportDetail(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_REPORT));
        return BackofficeReportResponseDTO.from(report);
    }

    @Override
    public BackofficeReportResponseDTO updateReportStatus(Long reportId, BackofficeReportStatusUpdateRequestDTO req) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_REPORT));

        ReportStatus nextStatus;
        try {
            nextStatus = ReportStatus.valueOf(req.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomAuthException(ErrorStatus._BAD_REQUEST);
        }

        report.updateStatus(nextStatus);

        reportRepository.save(report);

        return BackofficeReportResponseDTO.from(report);
    }
}