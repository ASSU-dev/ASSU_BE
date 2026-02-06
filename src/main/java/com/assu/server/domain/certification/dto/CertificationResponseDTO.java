package com.assu.server.domain.certification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public record CertificationResponseDTO(
	@Schema(description = "qr에 넣을 세션 아이디를 반환합니다.")
	Long sessionId) {

}
