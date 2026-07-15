package com.assu.server.domain.backoffice.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;

public record BackofficePaperCreateRequestDTO(
    @NotNull(message = "학생회 ID는 필수입니다.")
    Long adminId,

    @NotNull(message = "가게 ID는 필수입니다.")
    Long storeId,

    @NotNull(message = "제휴 시작일은 필수입니다.")
    LocalDate partnershipPeriodStart,

    @NotNull(message = "제휴 마감일은 필수입니다.")
    LocalDate partnershipPeriodEnd
) {
}
