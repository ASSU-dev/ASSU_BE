package com.assu.server.domain.map.dto;

import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import lombok.*;

import java.time.LocalDate;

public class MapResponseDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartnerMapResponseDTO {
        private Long partnerId;
        private String name;
        private String address;
        private boolean isPartnered;
        private Long partnershipId;
        private LocalDate partnershipStartDate;
        private LocalDate partnershipEndDate;
        private Double latitude;
        private Double longitude;
        private String profileUrl;
        private String phoneNumber;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminMapResponseDTO {
        private Long adminId;
        private String name;
        private String address;
        private boolean isPartnered;
        private Long partnershipId;
        private LocalDate partnershipStartDate;
        private LocalDate partnershipEndDate;
        private Double latitude;
        private Double longitude;
        private String profileUrl;
        private String phoneNumber;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StoreMapResponseDTO {
        private Long storeId;
        private Long adminId;
        private String adminName;
        private String name;
        private String address;
        private Integer rate;
        private CriterionType criterionType;
        private OptionType optionType;
        private Integer people;
        private Long cost;
        private String category;
        private String note;
        private Long discountRate;
        private boolean hasPartner;
        private Double latitude;
        private Double longitude;
        private String profileUrl;
        private String phoneNumber;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlaceSuggestionDTO {
        private String placeId;
        private String name;
        private String category;
        private String address;
        private String roadAddress;
        private String phone;
        private String placeUrl;
        private Double latitude;
        private Double longitude;
        private Integer distance;
    }
}
