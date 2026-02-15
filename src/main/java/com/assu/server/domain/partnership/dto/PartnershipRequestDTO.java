package com.assu.server.domain.partnership.dto;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import com.assu.server.domain.map.dto.SelectedPlacePayload;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Schema(description = "제휴 제안 요청")
public class PartnershipRequestDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WritePartnershipRequestDTO {
        private Long paperId;
        private LocalDate partnershipPeriodStart;
        private LocalDate partnershipPeriodEnd;
        private List<PartnershipOptionRequestDTO> options;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartnershipOptionRequestDTO {
        private OptionType optionType;
        private CriterionType criterionType;
        private Boolean anotherType;
        private Integer people;
        private Long cost;
        private String category;
        private Long discountRate;
        private String note;
        private List<PartnershipGoodsRequestDTO> goods;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartnershipGoodsRequestDTO {
        private String goodsName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequestDTO {
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualPartnershipRequestDTO {
        private String storeName;
        @NotNull private SelectedPlacePayload selectedPlace;
        private String storeDetailAddress;
        private LocalDate partnershipPeriodStart;
        private LocalDate partnershipPeriodEnd;
        private List<PartnershipOptionRequestDTO> options;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateDraftRequestDTO {
        private Long partnerId;
    }
}
