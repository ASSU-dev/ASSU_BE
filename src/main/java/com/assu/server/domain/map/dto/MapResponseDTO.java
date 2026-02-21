package com.assu.server.domain.map.dto;

import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

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
    public static class StoreMapResponseV2DTO {
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
        private String partner1;
        private String partner2;
        private String benefit1;
        private String benefit2;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PlaceSuggestionDTO {
        private String placeId;         // kakao place id
        private String name;            // place_name
        private String category;        // category_name or category_group_name
        private String address;         // 지번 주소
        private String roadAddress;     // 도로명 주소
        private String phone;           // 전화
        private String placeUrl;        // 카카오 상세 URL
        private Double latitude;        // y
        private Double longitude;       // x
        private Integer distance;       // m (좌표바이어스/카테고리 검색 시 제공)
    }
}
