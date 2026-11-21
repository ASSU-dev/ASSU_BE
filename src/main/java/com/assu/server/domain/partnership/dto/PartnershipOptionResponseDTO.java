package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.PaperContent;
import io.swagger.v3.oas.annotations.media.Schema;

public record PartnershipOptionResponseDTO(
        @Schema(description = "제휴 내용 ID", example = "3")
        Long contentId,

        @Schema(description = "혜택 조건", example = "30,000원 이상 주문 시")
        String benefitCondition,

        @Schema(description = "혜택 내용", example = "음료 1캔 제공")
        String benefit,

        @Schema(description = "유의사항", example = "학생증 지참")
        String caution,

        @Schema(description = "그룹 인증 최소 인원", example = "4")
        Integer minPeople
) {
    public static PartnershipOptionResponseDTO of(PaperContent pc) {
        return new PartnershipOptionResponseDTO(
                pc.getId(),
                pc.getBenefitCondition(),
                pc.getBenefit(),
                pc.getCaution(),
                pc.getMinPeople()
        );
    }
}
