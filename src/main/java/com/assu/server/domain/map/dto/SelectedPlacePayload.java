package com.assu.server.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectedPlacePayload {
    @Schema(description = "장소 ID", example = "12345678")
    private String placeId;

    @Schema(description = "장소 이름", example = "숭실대학교")
    private String name;

    @Schema(description = "장소 지번 주소", example = "서울특별시 동작구 상도로 369")
    private String address;

    @Schema(description = "장소 도로명 주소", example = "서울특별시 동작구 상도로 369")
    private String roadAddress;

    @Schema(description = "장소 위도", example = "37.50")
    private Double latitude;

    @Schema(description = "장소 경도", example = "126.96")
    private Double longitude;
}
