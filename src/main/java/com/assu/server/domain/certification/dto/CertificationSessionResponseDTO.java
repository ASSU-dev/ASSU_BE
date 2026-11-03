package com.assu.server.domain.certification.dto;

import com.assu.server.domain.partnership.dto.PaperContentResponseDTO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "그룹 인증 세션 상세 조회 응답")
public record CertificationSessionResponseDTO(
	@Schema(description = "세션 ID", example = "123")
	Long sessionId,

	@Schema(description = "매장 ID", example = "7")
	Long storeId,

	@Schema(description = "매장 이름", example = "숭실카페")
	String storeName,

	@Schema(description = "인증에 필요한 인원", example = "4")
	Integer peopleNumber,

	@Schema(description = "현재 인증 완료된 인원", example = "1")
	Integer currentCount,

	@Schema(description = "인증 대상 제휴 내용. 세션 생성 시 contentId를 보내지 않았다면 null")
	PaperContentResponseDTO partnershipContent
) {
}
