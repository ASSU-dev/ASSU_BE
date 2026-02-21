package com.assu.server.domain.qr.dto;

import com.assu.server.domain.qr.entity.SortByMethod;
import com.fasterxml.jackson.annotation.JsonFormat;

public record TemporaryQrResponseDTO(
	String adminName,
	SortByMethod sort,

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Seoul")
	String createdAt
) {

}
