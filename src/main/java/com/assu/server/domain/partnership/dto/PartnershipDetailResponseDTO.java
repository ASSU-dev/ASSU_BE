package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record PartnershipDetailResponseDTO(
        Long partnershipId,
        LocalDateTime updatedAt,
        LocalDate partnershipPeriodStart,
        LocalDate partnershipPeriodEnd,
        Long adminId,
        Long partnerId,
        Long storeId,
        List<PartnershipOptionResponseDTO> options
) {
    public static PartnershipDetailResponseDTO of(
            Paper paper,
            List<PaperContent> contents,
            List<List<Goods>> goodsBatches
    ) {
        List<LocalDateTime> allTimestamps = new ArrayList<>();

        if (paper.getUpdatedAt() != null) allTimestamps.add(paper.getUpdatedAt());
        if (contents != null) {
            contents.stream()
                    .map(BaseEntity::getUpdatedAt)
                    .filter(Objects::nonNull)
                    .forEach(allTimestamps::add);
        }
        if (goodsBatches != null) {
            goodsBatches.stream()
                    .flatMap(List::stream)
                    .map(BaseEntity::getUpdatedAt)
                    .filter(Objects::nonNull)
                    .forEach(allTimestamps::add);
        }

        LocalDateTime mostRecentUpdatedAt = allTimestamps.stream()
                .max(Comparator.naturalOrder())
                .orElse(paper.getUpdatedAt());

        List<PartnershipOptionResponseDTO> optionDTOs = new ArrayList<>();
        if (contents != null) {
            for (int i = 0; i < contents.size(); i++) {
                PaperContent pc = contents.get(i);
                List<Goods> goods = (goodsBatches != null && goodsBatches.size() > i)
                        ? goodsBatches.get(i) : List.of();
                optionDTOs.add(PartnershipOptionResponseDTO.of(pc, goods));
            }
        }

        return new PartnershipDetailResponseDTO(
                paper.getId(),
                mostRecentUpdatedAt,
                paper.getPartnershipPeriodStart(),
                paper.getPartnershipPeriodEnd(),
                paper.getAdmin() != null ? paper.getAdmin().getId() : null,
                paper.getPartner() != null ? paper.getPartner().getId() : null,
                paper.getStore() != null ? paper.getStore().getId() : null,
                optionDTOs
        );
    }
}
