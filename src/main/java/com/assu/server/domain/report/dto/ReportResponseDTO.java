package com.assu.server.domain.report.dto;

import lombok.Builder;

public class ReportResponseDTO {

    @Builder
    public record CreateReportResponse(
        Long reportId
    ) {
        public static CreateReportResponse of(Long reportId) {
            return CreateReportResponse.builder()
                    .reportId(reportId)
                    .build();
        }
    }
}
