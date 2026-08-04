package com.assu.server.domain.backoffice.dto;

import java.util.List;
import com.assu.server.domain.partnership.dto.PartnershipOptionRequestDTO;
import jakarta.validation.constraints.NotNull;

public record BackofficePaperContentCreateRequestDTO(
    @NotNull(message = "제휴 옵션 목록은 필수입니다.")
    List<PartnershipOptionRequestDTO> options
) {
}
