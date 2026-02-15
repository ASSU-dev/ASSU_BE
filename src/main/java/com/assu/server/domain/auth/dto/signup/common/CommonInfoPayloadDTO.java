package com.assu.server.domain.auth.dto.signup.common;

import com.assu.server.domain.map.dto.SelectedPlacePayload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "공통 정보 페이로드")
public record CommonInfoPayloadDTO(
        @Schema(description = "이름/업체명/단체명", example = "홍길동")
        @Size(min = 1, max = 50, message = "이름은 1~50자여야 합니다.")
        @NotBlank(message = "이름은 필수입니다.")
        String name,
        
        @Schema(description = "상세 주소", example = "101호")
        @Size(max = 255, message = "상세 주소는 255자를 넘을 수 없습니다.")
        String detailAddress,
        
        @Schema(description = "선택된 장소 정보")
        @NotNull(message = "선택된 장소 정보는 필수입니다.")
        SelectedPlacePayload selectedPlace
) {
}
