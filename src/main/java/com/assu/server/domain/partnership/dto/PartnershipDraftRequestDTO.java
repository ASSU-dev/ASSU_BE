package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.store.entity.Store;
import io.swagger.v3.oas.annotations.media.Schema;

public record PartnershipDraftRequestDTO(
        @Schema(description = "제휴 제안서를 작성할 제휴업체 ID", example = "101")
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
