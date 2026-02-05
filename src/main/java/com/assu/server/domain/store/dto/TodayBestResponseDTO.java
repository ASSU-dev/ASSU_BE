package com.assu.server.domain.store.dto;

import java.util.List;

public record TodayBestResponseDTO(
	List<String> bestStores
) {
}
