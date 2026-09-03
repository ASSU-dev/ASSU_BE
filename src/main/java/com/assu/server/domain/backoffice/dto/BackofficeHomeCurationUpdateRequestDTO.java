package com.assu.server.domain.backoffice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BackofficeHomeCurationUpdateRequestDTO(
    @NotBlank
    @Schema(description = "큐레이션 섹션 제목 ({name} 플레이스홀더 사용 가능)", example = "{name}님을 위한 제휴")
    String title,

    @NotNull
    @Schema(description = "상단 추천 업체 ID", example = "1")
    Long featuredStoreId,

    @Size(max = 255)
    @Schema(description = "상단 추천 할인 내용 문구 (미입력 시 제휴 기본 내용 적용)", example = "전 메뉴 1,000원 할인 또는 음료수 증정")
    String featuredDiscountContent,

    @NotEmpty
    @Size(min = 2, max = 2)
    @Valid
    @Schema(
        description = "추천 업체 그룹 목록 (반드시 2개 그룹)",
        example = "[{\"groupIndex\": 1, \"groupTitle\": \"인기 식사 혜택\", \"stores\": [{\"storeId\": 10, \"customDiscountContent\": \"10% 할인\", \"sortOrder\": 1}, {\"storeId\": 11, \"customDiscountContent\": \"음료 무료\", \"sortOrder\": 2}]}, {\"groupIndex\": 2, \"groupTitle\": \"카페/디저트\", \"stores\": [{\"storeId\": 20, \"customDiscountContent\": \"사이즈업\", \"sortOrder\": 1}, {\"storeId\": 21, \"customDiscountContent\": \"500원 할인\", \"sortOrder\": 2}]}]"
    )
    List<GroupUpdateRequestDTO> curationLists
) {

    public record GroupUpdateRequestDTO(
        @NotNull
        @Schema(description = "그룹 인덱스 (1 또는 2)", example = "1")
        Integer groupIndex,

        @Schema(description = "그룹 소제목", example = "인기 식사 혜택")
        String groupTitle,

        @NotEmpty
        @Size(min = 2, max = 2)
        @Valid
        @Schema(
            description = "그룹 내 추천 업체 목록 (반드시 2개)",
            example = "[{\"storeId\": 10, \"customDiscountContent\": \"10% 할인\", \"sortOrder\": 1}, {\"storeId\": 11, \"customDiscountContent\": \"음료 무료\", \"sortOrder\": 2}]"
        )
        List<StoreItemRequestDTO> stores
    ) {}

    public record StoreItemRequestDTO(
        @NotNull
        @Schema(description = "추천할 가게 ID", example = "2")
        Long storeId,

        @Size(max = 255)
        @Schema(description = "할인 및 혜택 문구 직접 지정 (선택)", example = "아메리카노 사이즈업")
        String customDiscountContent,

        @NotNull
        @Schema(description = "노출 순서 (1 또는 2)", example = "1")
        Integer sortOrder
    ) {}
}
