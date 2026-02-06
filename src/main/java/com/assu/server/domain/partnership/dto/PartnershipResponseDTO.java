package com.assu.server.domain.partnership.dto;


import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class PartnershipResponseDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WritePartnershipResponseDTO {
        private Long partnershipId;
        private LocalDate partnershipPeriodStart;
        private LocalDate partnershipPeriodEnd;
        private Long adminId;
        private Long partnerId;
        private Long storeId;
        private String storeName;
        private String adminName;
        private ActivationStatus isActivated;
        private List<PartnershipOptionResponseDTO> options;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartnershipOptionResponseDTO {
        private OptionType optionType;
        private CriterionType criterionType;
        private Boolean anotherType;
        private Integer people;
        private Long cost;
        private String note;
        private String category;
        private Long discountRate;

        private List<PartnershipGoodsResponseDTO> goods;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartnershipGoodsResponseDTO {
        private Long goodsId;
        private String goodsName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateResponseDTO {
        private Long partnershipId;
        private String prevStatus;
        private String newStatus;
        private LocalDateTime changedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ManualPartnershipResponseDTO {
        private Long storeId;
        private boolean storeCreated;
        private boolean storeActivated;
        private String status;
        private String contractImageUrl;
        private WritePartnershipResponseDTO partnership;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateDraftResponseDTO {
        private Long paperId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspendedPaperDTO {
        private Long paperId;
        private String partnerName;
        private LocalDateTime createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminPartnershipWithPartnerResponseDTO {
        private Long paperId;
        private boolean isPartnered;
        private String status;
        private Long partnerId;
        private String partnerName;
        private String partnerAddress;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartnerPartnershipWithAdminResponseDTO {
        private Long paperId;
        private boolean isPartnered;
        private String status;
        private Long adminId;
        private String adminName;
        private String adminAddress;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GetPartnershipDetailResponseDTO {
        private Long partnershipId;
        private LocalDateTime updatedAt;
        private LocalDate partnershipPeriodStart;
        private LocalDate partnershipPeriodEnd;
        private Long adminId;
        private Long partnerId;
        private Long storeId;
        private List<PartnershipOptionResponseDTO> options;
    }
}
