package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.map.dto.SelectedPlacePayload;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.store.entity.Store;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record ManualPartnershipRequestDTO(
        String storeName,
        @NotNull SelectedPlacePayload selectedPlace,
        String storeDetailAddress,
        LocalDate partnershipPeriodStart,
        LocalDate partnershipPeriodEnd,
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
