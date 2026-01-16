package com.assu.server.domain.certification.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record CertificationProgressResponseDTO (
	@Schema(description = "progress/completed 중 1개 선택하여 string으로 입력")
	String type,
	@Schema(description = "현재 인증 완료된 인원 수")
	Integer count,
	@Schema(description = "인증이 모두 완료되었을 시 완료 메세지 첨부하여 전송됨")
	String message,
	@Schema(description = "인증에 포함된 userId를 반환")
	List<Long> userIds
){
}