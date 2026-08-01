package com.assu.server.domain.backoffice.dto;

import java.time.LocalDateTime;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.report.entity.Report;

public record BackofficeReportResponseDTO(
        Long reportId,
        Long reporterId,
        String reporterName,
        String targetType,
        Long targetId,
        Long reportedId,
        String reportType,
        String status,
        LocalDateTime createdAt
) {
    public static BackofficeReportResponseDTO from(Report report) {
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

    private static String extractName(Member member) {
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