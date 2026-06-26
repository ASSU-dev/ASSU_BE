package com.assu.server.domain.backoffice.dto;

import java.time.LocalDate;
import java.util.List;

import com.assu.server.domain.map.dto.SelectedPlacePayload;
import com.assu.server.domain.partnership.dto.PartnershipOptionRequestDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record BackofficePartnershipCreateRequestDTO(
    @Schema(description = "학생회(Admin) ID", example = "1")
    @NotNull(message = "학생회 ID는 필수입니다.")
    Long adminId,

    @Schema(description = "가게 이름", example = "역전할머니맥주 숭실대점")
    @NotNull(message = "가게 이름은 필수입니다.")
    String storeName,

    @Schema(description = "선택된 장소 정보 (카카오맵 검색 결과)")
    @NotNull(message = "장소 정보는 필수입니다.")
    SelectedPlacePayload selectedPlace,

    @Schema(description = "가게 상세주소", example = "2층")
    String storeDetailAddress,

    @Schema(description = "제휴 시작일", example = "2024-01-01")
    @NotNull(message = "제휴 시작일은 필수입니다.")
    LocalDate partnershipPeriodStart,

    @Schema(description = "제휴 마감일", example = "2024-12-31")
    @NotNull(message = "제휴 마감일은 필수입니다.")
    LocalDate partnershipPeriodEnd,

    @Schema(description = "제휴 옵션 목록")
    @NotNull(message = "제휴 옵션 목록은 필수입니다.")
    List<PartnershipOptionRequestDTO> options
) {
}
