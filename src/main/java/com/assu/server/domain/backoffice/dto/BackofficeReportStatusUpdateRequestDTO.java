package com.assu.server.domain.backoffice.dto;

import jakarta.validation.constraints.NotBlank;

public record BackofficeReportStatusUpdateRequestDTO(
        @NotBlank(message = "변경할 상태 값은 필수입니다.")
        String status
) {}