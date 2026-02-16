package com.assu.server.domain.qr.dto;

import com.assu.server.domain.qr.entity.Qr;
import com.assu.server.domain.qr.entity.SortByMethod;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TemporaryQrRequestDTO(
	@Schema(description = "adminName을 입력해주세요")
	String adminName,
	@Schema(description = "프론트 단에서 enum을 정의하여 REVIEW/SUGGEST 둘중 하나로 넘겨주세요")
	@NotNull(message = "ENUM type REVIEW/SUGGEST 둘중 하나를 입력해주세요")
	SortByMethod sort
) {

	public static Qr toQr(TemporaryQrRequestDTO dto, Long userId){
		return Qr.builder()
			.adminName(dto.adminName)
			.userId(userId)
			.sort(dto.sort)
			.build();
	}
}
