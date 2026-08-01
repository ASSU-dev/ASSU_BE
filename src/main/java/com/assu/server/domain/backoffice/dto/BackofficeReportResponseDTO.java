package com.assu.server.domain.backoffice.dto;

import java.time.LocalDateTime;

public record BackofficeReportResponseDTO(
        Long reportId,
        Long reporterId,
        String reporterName,
        String targetType,
        Long targetId,
        Long reportedId,
        String content,
        String status,
        LocalDateTime createdAt
) {}