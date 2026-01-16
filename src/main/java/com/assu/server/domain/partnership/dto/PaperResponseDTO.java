package com.assu.server.domain.partnership.dto;

import java.util.List;

public record PaperResponseDTO (
	List<PaperContentResponseDTO> partnershipContents,
	String storeName,
	Long storeId
){
}