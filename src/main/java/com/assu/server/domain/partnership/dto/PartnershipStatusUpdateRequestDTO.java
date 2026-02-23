package com.assu.server.domain.partnership.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PartnershipStatusUpdateRequestDTO(
        @Schema(description = "제안서에 적용할 상태 (ACTIVE/SUSPEND/INACTIVE)", example = "ACTIVE")
        @NotNull String status
) {
}
