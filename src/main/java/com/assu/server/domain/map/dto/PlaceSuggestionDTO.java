package com.assu.server.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlaceSuggestionDTO(
        @Schema(description = "장소 ID", example = "12345678")
        String placeId,

        @Schema(description = "장소 이름", example = "숭실대학교")
        String name,

        @Schema(description = "장소 카테고리", example = "대학교")
        String category,

        @Schema(description = "장소 지번 주소", example = "서울특별시 동작구 상도로 369")
        String address,

        @Schema(description = "장소 도로명 주소", example = "서울특별시 동작구 상도로 369")
        String roadAddress,

        @Schema(description = "장소 전화번호", example = "02-820-0114")
        String phone,

        @Schema(description = "카카오맵 장소 Url", example = "https://place.map.kakao.com/12345678")
        String placeUrl,

        @Schema(description = "장소 위도", example = "37.50")
        Double latitude,

        @Schema(description = "장소 경도", example = "126.96")
        Double longitude,

        @Schema(description = "현재 위치로부터의 거리 (m)", example = "100")
        Integer distance
) {}
