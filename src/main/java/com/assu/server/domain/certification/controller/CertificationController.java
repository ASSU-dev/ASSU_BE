package com.assu.server.domain.certification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.certification.dto.CertificationGroupRequestDTO;
import com.assu.server.domain.certification.dto.CertificationPersonalRequestDTO;
import com.assu.server.domain.certification.dto.CertificationResponseDTO;
import com.assu.server.domain.certification.service.CertificationService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@Tag(name = "제휴 인증 api", description = "qr인증과 관련된 api 입니다.")
@RequiredArgsConstructor
public class CertificationController {

	private final CertificationService certificationService;

	@PostMapping("/certification/session")
	@Operation(
		summary = "세션 정보 요청 API",
		description = "# [v1.0 (2025-09-09)](https://www.notion.so/22b1197c19ed80bb8484d99cc6ce715b?source=copy_link)\n" +
			"- `multipart/form-data`로 호출합니다.\n" +
			"- 파트: `payload`(JSON, CertificationRequest.groupRequest)\n" +
			"- 처리: 정보 바탕으로 sessionManager에 session생성\n" +
			"- 성공 시 201(Created)과 생성된 memberId 반환.\n" +
			"\n**Request Parts:**\n" +
			"  - `request` (JSON, required): `CertificationRequest.groupRequest` 객체\n" +
			"  - `people` (Integer, required): 인증이 필요한 인원\n" +
			"  - `storeId` (Long, required): 스토어 id\n"+
			"  - `adminId` (Long, required): 관리자 id\n"+
			"  - `tableNumber` (Integer, required): 테이블 넘버\n"+
			"\n**Response:**\n" +
			"  - 성공 시 201(Created)와 sessionId, adminId 반환"
	)
	public ResponseEntity<BaseResponse<CertificationResponseDTO>> getSessionId(
		@AuthenticationPrincipal PrincipalDetails pd,
		@RequestBody CertificationGroupRequestDTO dto
	) {

		CertificationResponseDTO result = certificationService.getSessionId(dto, pd.getMember());

		return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus.GROUP_SESSION_CREATE, result));
	}

	@PostMapping("/certification/personal")
	@Operation(
		summary = "개인 인증 요청 API",
		description =
			"# [v1.0 (2025-09-09)](https://clumsy-seeder-416.notion.site/2471197c19ed80fd9a8dcc43fb938a5d?source=copy_link)\n" +
				"- 개인 단위 인증을 위한 API입니다.\n" +
				"- 그룹 인증이 아닌 경우, 통계 및 제휴 이력 적재를 목적으로 사용됩니다.\n" +
				"- 가게별 제휴 조회 시 `people` 값이 null 인 경우 호출됩니다.\n" +
				"\n**Request Body:**\n" +
				"  - `storeId` (Long, required): 인증이 발생한 스토어 ID\n" +
				"  - `adminId` (Long, required): 인증을 요청한 관리자 ID\n" +
				"  - `tableNumber` (Integer, required): 인증이 발생한 테이블 번호\n" +
				"\n**Authentication:**\n" +
				"  - 로그인된 사용자 인증 정보 필요 (`@AuthenticationPrincipal`)\n" +
				"\n**Processing:**\n" +
				"  - 전달받은 정보 기반으로 개인 인증 이력 저장\n" +
				"  - 세션 생성은 하지 않음\n" +
				"\n**Response:**\n" +
				"  - 성공 시 200(OK)\n" +
				"  - 성공 메시지 반환"
	)
	public ResponseEntity<BaseResponse<String>> personalCertification(
		@AuthenticationPrincipal PrincipalDetails pd,
		@RequestBody CertificationPersonalRequestDTO dto
	) {
		certificationService.certificatePersonal(dto, pd.getMember());

		return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus.PERSONAL_CERTIFICATION_SUCCESS, "null"));
	}

}
