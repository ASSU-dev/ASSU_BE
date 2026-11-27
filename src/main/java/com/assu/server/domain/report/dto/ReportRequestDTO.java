package com.assu.server.domain.report.dto;

import com.assu.server.domain.report.entity.enums.ReportTargetType;
import com.assu.server.domain.report.entity.enums.ReportType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

public class ReportRequestDTO {

    @Builder
    public record CreateContentReportRequest(
        @NotNull(message = "신고 대상 타입은 필수입니다.") ReportTargetType targetType,
        @NotNull(message = "신고 대상 ID는 필수입니다.") Long targetId,
        @NotNull(message = "신고 유형은 필수입니다.") ReportType reportType
    ) {}

    @Builder
    public record CreateStudentReportRequest(
        @NotNull(message = "신고 대상의 작성 컨텐츠의 타입은 필수입니다.") ReportTargetType targetType,
        @NotNull(message = "신고 대상의 작성 컨텐츠 ID는 필수입니다.") Long targetId,
        @NotNull(message = "유저 신고 유형은 필수입니다.") ReportType reportType
    ) {}
}