package com.assu.server.domain.partnership.dto;
import java.util.List;
import lombok.Getter;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.map.dto.SelectedPlacePayload;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

public class PartnershipRequestDTO {
    @Getter
    public static class finalRequest{
        Long storeId;
        String tableNumber;
        String adminName;
        String placeName;
        String partnershipContent;
        Long contentId;
        Long discount;
        List<Long> userIds;
    }

    @Getter
    public static class WritePartnershipRequestDTO {
        private Long paperId;
        private LocalDate partnershipPeriodStart;
        private LocalDate partnershipPeriodEnd;
        private List<PartnershipOptionRequestDTO> options;
    }

    @Getter
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
    public static class PartnershipGoodsRequestDTO {
        private String goodsName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequestDTO {
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ManualPartnershipRequestDTO {
        private String storeName;
        @NotNull private SelectedPlacePayload selectedPlace;
        private String storeDetailAddress;
        private LocalDate partnershipPeriodStart;
        private LocalDate partnershipPeriodEnd;
        private List<PartnershipOptionRequestDTO> options;
    }

    @Getter
    public static class CreateDraftRequestDTO {
        private Long partnerId;
    }
}
