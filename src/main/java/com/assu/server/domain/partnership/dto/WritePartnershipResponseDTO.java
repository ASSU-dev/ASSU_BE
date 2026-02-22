package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record WritePartnershipResponseDTO(
        Long partnershipId,
        LocalDate partnershipPeriodStart,
        LocalDate partnershipPeriodEnd,
        Long adminId,
        Long partnerId,
        Long storeId,
        String storeName,
        String adminName,
        ActivationStatus isActivated,
        List<PartnershipOptionResponseDTO> options
) {
    public static WritePartnershipResponseDTO of(
            Paper paper,
            List<PaperContent> contents,
            List<List<Goods>> goodsBatches
    ) {
        List<PartnershipOptionResponseDTO> optionDTOs = new ArrayList<>();
        if (contents != null) {
            for (int i = 0; i < contents.size(); i++) {
                PaperContent pc = contents.get(i);
                List<Goods> goods = (goodsBatches != null && goodsBatches.size() > i)
                        ? goodsBatches.get(i) : List.of();
                optionDTOs.add(PartnershipOptionResponseDTO.of(pc, goods));
            }
        }
        return new WritePartnershipResponseDTO(
                paper.getId(),
                paper.getPartnershipPeriodStart(),
                paper.getPartnershipPeriodEnd(),
                paper.getAdmin() != null ? paper.getAdmin().getId() : null,
                paper.getPartner() != null ? paper.getPartner().getId() : null,
                paper.getStore() != null ? paper.getStore().getId() : null,
                paper.getStore().getName(),
                paper.getAdmin().getName(),
                paper.getIsActivated(),
                optionDTOs
        );
    }
}
