package com.assu.server.domain.qr.dto;

import com.assu.server.domain.qr.entity.SortByMethod;

public record TemporaryQrResponseDTO(
	String adminName,
	SortByMethod sort
) {

}
