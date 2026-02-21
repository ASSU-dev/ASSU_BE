package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.store.entity.Store;

public record PartnershipDraftRequestDTO(
        Long partnerId
) {
    public Paper toDraftPaper(Admin admin, Partner partner, Store store) {
        return Paper.builder()
                .admin(admin)
                .partner(partner)
                .store(store)
                .partnershipPeriodStart(null)
                .partnershipPeriodEnd(null)
                .isActivated(ActivationStatus.BLANK)
                .contractImageKey(null)
                .build();
    }
}
