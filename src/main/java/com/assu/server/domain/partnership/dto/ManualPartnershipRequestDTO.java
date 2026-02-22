package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.map.dto.SelectedPlacePayload;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.store.entity.Store;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record ManualPartnershipRequestDTO(
        @Schema(description = "가게 이름", example = "역전할머니맥주 숭실대점")
        String storeName,

        @Schema(description = "선택된 장소 정보 (카카오맵 검색 결과)")
        @NotNull SelectedPlacePayload selectedPlace,

        @Schema(description = "가게 상세주소", example = "2층")
        String storeDetailAddress,

        @Schema(description = "제휴 시작일", example = "2024-01-01")
        LocalDate partnershipPeriodStart,

        @Schema(description = "제휴 마감일", example = "2024-12-31")
        LocalDate partnershipPeriodEnd,

        @Schema(description = "제휴 옵션 목록")
        List<PartnershipOptionRequestDTO> options
) {
    public Paper toPaper(Admin admin, Store store, ActivationStatus status) {
        return Paper.builder()
                .admin(admin)
                .store(store)
                .partner(null)
                .isActivated(status)
                .partnershipPeriodStart(partnershipPeriodStart())
                .partnershipPeriodEnd(partnershipPeriodEnd())
                .build();
    }

    public List<PaperContent> toPaperContents(Paper paper) {
        if (options() == null || options().isEmpty()) return List.of();
        List<PaperContent> list = new ArrayList<>(options().size());
        for (var o : options()) {
            list.add(o.toPaperContent(paper));
        }
        return list;
    }
}
