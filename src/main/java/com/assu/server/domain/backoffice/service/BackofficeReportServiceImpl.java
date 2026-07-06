package com.assu.server.domain.backoffice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.assu.server.domain.backoffice.dto.BackofficeReportResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeReportStatusUpdateRequestDTO;
import com.assu.server.domain.member.entity.Member;
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
        return reports.map(this::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public BackofficeReportResponseDTO getReportDetail(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_REPORT));
        return toResponseDTO(report);
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

        return toResponseDTO(report);
    }

    private BackofficeReportResponseDTO toResponseDTO(Report report) {
        String reporterName = extractName(report.getReporter());

        return new BackofficeReportResponseDTO(
                report.getId(),
                report.getReporter() != null ? report.getReporter().getId() : null,
                reporterName,
                report.getTargetType() != null ? report.getTargetType().name() : null,
                report.getTargetId(),
                report.getReported() != null ? report.getReported().getId() : null,
                report.getReportType() != null ? report.getReportType().name() : null,
                report.getStatus() != null ? report.getStatus().name() : null,
                report.getCreatedAt()
        );
    }


    private String extractName(Member member) {
        if (member == null || member.getRole() == null) {
            return "탈퇴 사용자";
        }

        return switch (member.getRole()) {
            case STUDENT -> (member.getStudentProfile() != null) ? member.getStudentProfile().getName() : "미등록 학생";
            case ADMIN -> (member.getAdminProfile() != null) ? member.getAdminProfile().getName() : "미등록 관리자";
            case PARTNER -> (member.getPartnerProfile() != null) ? member.getPartnerProfile().getName() : "미등록 파트너";
            case BACKOFFICE -> (member.getBackofficeProfile() != null) ? member.getBackofficeProfile().getName() : "백오피스 운영자";
        };
    }
}