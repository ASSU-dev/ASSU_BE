package com.assu.server.domain.qr.dto;

import com.assu.server.domain.qr.entity.Qr;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TemporaryQrRequestDTO(
	@Schema(description = "storeId를 입력해주세요")
	@NotNull(message = "storeId를 입력해주세요")
	Long storeId,
	@Schema(description = "userId를 입력해주세요")
	@NotNull(message = "userId를 입력해주세요")
	Long userId
) {

	public Qr toQr(){
		return Qr.builder()
			.storeId(this.storeId)
			.userId(this.userId)
			.build();
	}
}
