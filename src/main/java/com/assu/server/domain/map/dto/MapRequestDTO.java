package com.assu.server.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MapRequestDTO(
        @Schema(description = "화면 좌상단 경도")
        @NotNull(message = "경도를 입력해주세요.")
        double lng1,

        @Schema(description = "화면 좌상단 위도")
        @NotNull(message = "위도를 입력해주세요.")
        double lat1,

        @Schema(description = "화면 우상단 경도")
        @NotNull(message = "경도를 입력해주세요.")
        double lng2,

        @Schema(description = "화면 우상단 위도")
        @NotNull(message = "위도를 입력해주세요.")
        double lat2,

        @Schema(description = "화면 우하단 경도")
        @NotNull(message = "경도를 입력해주세요.")
        double lng3,

        @Schema(description = "화면 우하단 위도")
        @NotNull(message = "위도를 입력해주세요.")
        double lat3,

        @Schema(description = "화면 좌하단 경도")
        @NotNull(message = "경도를 입력해주세요.")
        double lng4,

        @Schema(description = "화면 좌하단 위도")
        @NotNull(message = "위도를 입력해주세요.")
        double lat4
) {}