package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.store.entity.enums.LinkType;

import java.util.List;

public record PaperResponseDTO (
	List<PaperContentResponseDTO> partnershipContents,
	String storeName,
	Long storeId,
	LinkType linkType
){
}