package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record WritePartnershipResponseDTO(
        @Schema(description = "제안서 ID", example = "1001")
        Long partnershipId,

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

        @Schema(description = "가게 이름", example = "역전할머니맥주 숭실대점")
        String storeName,

        @Schema(description = "관리자 이름", example = "숭실대학교 총학생회")
        String adminName,

        @Schema(description = "제안서 활성화 여부", example = "SUSPEND")
        ActivationStatus isActivated,

        @Schema(description = "제휴 옵션 목록")
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
