package com.assu.server.domain.partnership.dto;

import java.util.List;

public record PaperContentResponseDTO(
	Long adminId,
	String adminName,
	String paperContent,
	Long contentId,
	List<String> goods,
	Integer people,
	Long cost
) {

}