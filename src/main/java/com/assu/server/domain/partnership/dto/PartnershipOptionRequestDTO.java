package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnershipOptionRequestDTO(
        @Schema(description = "혜택 조건", example = "30,000원 이상 주문 시")
        @Size(max = 255)
        String benefitCondition,

        @Schema(description = "혜택 내용", example = "음료 1캔 제공")
        @NotBlank
        String benefit,

        @Schema(description = "유의사항", example = "학생증 지참")
        @Size(max = 255)
        String caution,

        @Schema(description = "그룹 인증 최소 인원", example = "4")
        @Min(1)
        Integer minPeople
) {
    public PaperContent toPaperContent(Paper paper) {
        return PaperContent.builder()
                .paper(paper)
                .benefitCondition(benefitCondition())
                .benefit(benefit())
                .caution(caution())
                .minPeople(minPeople())
                .build();
    }
}
