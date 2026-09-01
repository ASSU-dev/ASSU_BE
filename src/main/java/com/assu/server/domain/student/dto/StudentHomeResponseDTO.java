package com.assu.server.domain.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record StudentHomeResponseDTO(
    @Schema(description = "상단 추천 업체 및 할인 정보")
    FeaturedRecommendationDTO featuredRecommendation,

    @Schema(description = "큐레이션 섹션 제목 (학생 이름 적용 완료)", example = "홍길동님을 위한 제휴")
    String curationTitle,

    @Schema(description = "추천 업체 그룹 목록 (총 2개 리스트, 각 리스트당 2개 업체)")
    List<CurationGroupDTO> curationLists
) {

    public record FeaturedRecommendationDTO(
        @Schema(description = "추천 업체 ID", example = "1")
        Long storeId,

        @Schema(description = "추천 업체 이름", example = "더진국 숭실대점")
        String storeName,

        @Schema(description = "추천 할인 내용", example = "전 메뉴 1,000원 할인 또는 음료수 증정")
        String discountContent,

        @Schema(description = "업체 카테고리", example = "RESTAURANT")
        String storeCategory,

        @Schema(description = "업체 프로필 이미지 URL", example = "https://assu-s3.s3.ap-northeast-2.amazonaws.com/profile.png")
        String profileImageUrl
    ) {
        public static FeaturedRecommendationDTO of(
            Long storeId,
            String storeName,
            String discountContent,
            String storeCategory,
            String profileImageUrl
        ) {
            return new FeaturedRecommendationDTO(storeId, storeName, discountContent, storeCategory, profileImageUrl);
        }
    }

    public record CurationGroupDTO(
        @Schema(description = "그룹 인덱스 (1 또는 2)", example = "1")
        Integer groupIndex,

        @Schema(description = "그룹 소제목", example = "인기 식사 혜택")
        String groupTitle,

        @Schema(description = "그룹 내 추천 업체 목록 (2개)")
        List<CurationStoreDTO> stores
    ) {
        public static CurationGroupDTO of(
            Integer groupIndex,
            String groupTitle,
            List<CurationStoreDTO> stores
        ) {
            return new CurationGroupDTO(groupIndex, groupTitle, stores);
        }
    }

    public record CurationStoreDTO(
        @Schema(description = "가게 ID", example = "2")
        Long storeId,

        @Schema(description = "가게 이름", example = "스타벅스 숭실대점")
        String storeName,

        @Schema(description = "할인 및 혜택 내용", example = "아메리카노 사이즈업")
        String discountContent,

        @Schema(description = "가게 카테고리", example = "CAFE")
        String storeCategory,

        @Schema(description = "가게 프로필 이미지 URL", example = "https://assu-s3.s3.ap-northeast-2.amazonaws.com/store.png")
        String profileImageUrl
    ) {
        public static CurationStoreDTO of(
            Long storeId,
            String storeName,
            String discountContent,
            String storeCategory,
            String profileImageUrl
        ) {
            return new CurationStoreDTO(storeId, storeName, discountContent, storeCategory, profileImageUrl);
        }
    }

    public static StudentHomeResponseDTO of(
        FeaturedRecommendationDTO featuredRecommendation,
        String curationTitle,
        List<CurationGroupDTO> curationLists
    ) {
        return new StudentHomeResponseDTO(featuredRecommendation, curationTitle, curationLists);
    }
}
