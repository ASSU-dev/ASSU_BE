package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record PartnershipDetailResponseDTO(
        @Schema(description = "제안서 ID", example = "1001")
        Long partnershipId,

        @Schema(description = "제안서 최종 수정 시간", example = "2024-06-15T10:30:00")
        LocalDateTime updatedAt,

        @Schema(description = "제휴 시작일", example = "2024-01-01")
        LocalDate partnershipPeriodStart,

        @Schema(description = "제휴 마감일", example = "2024-12-31")
        LocalDate partnershipPeriodEnd,

        @Schema(description = "관리자 ID", example = "101")
        Long adminId,

        @Schema(description = "제휴업체 ID", example = "201")
        Long partnerId,

        @Schema(description = "가게 ID", example = "301")
        Long storeId,

        @Schema(description = "제휴 옵션 목록")
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
