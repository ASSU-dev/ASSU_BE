package com.assu.server.domain.backoffice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record BackofficeHomeCurationResponseDTO(
    @Schema(description = "큐레이션 설정 고유 ID", example = "1")
    Long curationId,

    @Schema(description = "설정된 큐레이션 제목 템플릿", example = "{name}님을 위한 제휴")
    String title,

    @Schema(
        description = "상단 추천 업체 및 할인 정보",
        example = "{\"storeId\": 1, \"storeName\": \"더진국 숭실대점\", \"discountContent\": \"전 메뉴 1,000원 할인 또는 음료수 증정\", \"storeCategory\": \"RESTAURANT\"}"
    )
    FeaturedDTO featuredRecommendation,

    @Schema(
        description = "추천 큐레이션 그룹 목록 (2개 그룹 x 2개 업체)",
        example = "[{\"groupIndex\": 1, \"groupTitle\": \"추천 제휴 1\", \"stores\": [{\"storeId\": 10, \"storeName\": \"가게 A\", \"discountContent\": \"10% 할인\", \"storeCategory\": \"RESTAURANT\", \"sortOrder\": 1}, {\"storeId\": 11, \"storeName\": \"가게 B\", \"discountContent\": \"음료 무료\", \"storeCategory\": \"RESTAURANT\", \"sortOrder\": 2}]}, {\"groupIndex\": 2, \"groupTitle\": \"추천 제휴 2\", \"stores\": [{\"storeId\": 20, \"storeName\": \"가게 C\", \"discountContent\": \"사이즈업\", \"storeCategory\": \"CAFE\", \"sortOrder\": 1}, {\"storeId\": 21, \"storeName\": \"가게 D\", \"discountContent\": \"500원 할인\", \"storeCategory\": \"CAFE\", \"sortOrder\": 2}]}]"
    )
    List<GroupDTO> curationLists
) {

    public record FeaturedDTO(
        @Schema(description = "추천 업체 ID", example = "1")
        Long storeId,

        @Schema(description = "추천 업체 이름", example = "더진국 숭실대점")
        String storeName,

        @Schema(description = "추천 할인 내용", example = "전 메뉴 1,000원 할인 또는 음료수 증정")
        String discountContent,

        @Schema(description = "업체 카테고리", example = "RESTAURANT")
        String storeCategory
    ) {
        public static FeaturedDTO of(
            Long storeId,
            String storeName,
            String discountContent,
            String storeCategory
        ) {
            return new FeaturedDTO(storeId, storeName, discountContent, storeCategory);
        }
    }

    public record GroupDTO(
        @Schema(description = "그룹 인덱스 (1 또는 2)", example = "1")
        Integer groupIndex,

        @Schema(description = "그룹 소제목", example = "인기 식사 혜택")
        String groupTitle,

        @Schema(
            description = "그룹 내 추천 업체 목록 (2개)",
            example = "[{\"storeId\": 10, \"storeName\": \"가게 A\", \"discountContent\": \"10% 할인\", \"storeCategory\": \"RESTAURANT\", \"sortOrder\": 1}, {\"storeId\": 11, \"storeName\": \"가게 B\", \"discountContent\": \"음료 무료\", \"storeCategory\": \"RESTAURANT\", \"sortOrder\": 2}]"
        )
        List<StoreItemDTO> stores
    ) {
        public static GroupDTO of(
            Integer groupIndex,
            String groupTitle,
            List<StoreItemDTO> stores
        ) {
            return new GroupDTO(groupIndex, groupTitle, stores);
        }
    }

    public record StoreItemDTO(
        @Schema(description = "가게 ID", example = "2")
        Long storeId,

        @Schema(description = "가게 이름", example = "스타벅스 숭실대점")
        String storeName,

        @Schema(description = "할인 및 혜택 내용", example = "아메리카노 사이즈업")
        String discountContent,

        @Schema(description = "가게 카테고리", example = "CAFE")
        String storeCategory,

        @Schema(description = "노출 순서", example = "1")
        Integer sortOrder
    ) {
        public static StoreItemDTO of(
            Long storeId,
            String storeName,
            String discountContent,
            String storeCategory,
            Integer sortOrder
        ) {
            return new StoreItemDTO(storeId, storeName, discountContent, storeCategory, sortOrder);
        }
    }

    public static BackofficeHomeCurationResponseDTO of(
        Long curationId,
        String title,
        FeaturedDTO featuredRecommendation,
        List<GroupDTO> curationLists
    ) {
        return new BackofficeHomeCurationResponseDTO(curationId, title, featuredRecommendation, curationLists);
    }
}
