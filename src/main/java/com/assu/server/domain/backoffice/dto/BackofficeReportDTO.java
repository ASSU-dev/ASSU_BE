package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.report.entity.Report;
import com.assu.server.domain.report.entity.enums.ReportStatus;
import com.assu.server.domain.report.entity.enums.ReportTargetType;
import com.assu.server.domain.report.entity.enums.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class BackofficeReportDTO {

    @Schema(description = "콘텐츠 소프트 삭제 응답 DTO")
    public record SoftDeleteResponseDTO(
            Long id,
            String status
    ) {
        public static SoftDeleteResponseDTO of(Long id) {
            return new SoftDeleteResponseDTO(id, "DELETED");
        }
    }

    @Schema(description = "신고 기각 응답 DTO")
    public record RejectReportResponseDTO(
            Long reportId,
            String status
    ) {
        public static RejectReportResponseDTO of(Long reportId) {
            return new RejectReportResponseDTO(reportId, "REJECTED");
        }
    }

    @Schema(description = "신고 조회 응답 DTO")
    public record ReportListItemDTO(
            Long reportId,
            Long reporterId,
            ReportTargetType targetType,
            Long targetId,
            Long reportedMemberId,
            ReportType reportType,
            ReportStatus status,
            LocalDateTime createdAt
    ) {
        public static ReportListItemDTO from(Report report) {
            return new ReportListItemDTO(
                    report.getId(),
                    report.getReporter().getId(),
                    report.getTargetType(),
                    report.getTargetId(),
                    report.getReported() != null ? report.getReported().getId() : null,
                    report.getReportType(),
                    report.getStatus(),
                    report.getCreatedAt()
            );
        }

        public static List<ReportListItemDTO> fromList(List<Report> reports) {
            return reports.stream().map(ReportListItemDTO::from).toList();
        }
    }
}