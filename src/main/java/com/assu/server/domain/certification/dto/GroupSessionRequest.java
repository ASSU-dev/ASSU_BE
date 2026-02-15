package com.assu.server.domain.certification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record GroupSessionRequest(
	@Schema(description = "adminId를 입력해주세요")
	@NotNull(message = "adminId를 입력해주세요")
	Long adminId,
	@Schema(description = "qr에서 읽은 세션아이디를 입력해주세요")
	@NotNull(message = "인증하고자 하는 session의 sessionId를 입력해주세요")
	Long sessionId) {

	@Override
	public String toString() {
		return "GroupSessionRequest{" +
			"adminId=" + adminId +
			", sessionId=" + sessionId +
			'}';
	}
}