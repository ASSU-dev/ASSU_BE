package com.assu.server.domain.qr.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.qr.dto.TemporaryQrRequestDTO;
import com.assu.server.domain.qr.service.TemporaryQrService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name="임시 QR api", description="1학기 임시 운영 qr 관련 api 입니다.")
@RequestMapping("/temporary-qr")
public class TemporaryQrController {

	private final TemporaryQrService temporaryQrService;

	@PostMapping("/data")
	@Operation(
		summary = "QR 데이터 삽입 API",
		description =
			"# [v1.0 (2026-02-03)]\n" +
				"- 임시 QR 적립 데이터를 저장합니다.\n" +
				"- 요청 바디(JSON)를 통해 QR 적립 대상 정보를 전달합니다.\n" +
				"\n**Request Body:**\n" +
				"  - `storeId` (Long, required): 매장 ID\n" +
				"  - `userId` (Long, required): 사용자 ID\n" +
				"  - `sort` (Enum, required): 어떻게 적립되었는지 REVIEW/SUGGEST 중 하나 입력\n" +
				"\n**Response:**\n" +
				"  - 성공 시 200(OK)\n"
	)
	public ResponseEntity<BaseResponse<Void>> insertQrData(TemporaryQrRequestDTO dto,
		@AuthenticationPrincipal PrincipalDetails pd){
		temporaryQrService.insertData(dto, pd.getMember());
		return ResponseEntity.ok(BaseResponse.onSuccessWithoutData(SuccessStatus._OK));
	}

	// @PatchMapping("/stamp")
	// @Operation(
	// 	summary = "리뷰/건의 이후 단순 스탬프 증가 API",
	// 	description =
	// 		"# [v1.0 (2026-02-03)]\n" +
	// 			"- 리뷰 또는 건의 작성 이후 호출되는 API입니다.\n" +
	// 			"- 로그인된 사용자(Principal)를 기준으로 스탬프를 1 증가시킵니다.\n" +
	// 			"- Request Body는 없으며, 인증 정보만 필요합니다.\n" +
	// 			"\n**Request:**\n" +
	// 			"  - Authorization Header 필요 (JWT)\n" +
	// 			"\n**Response:**\n" +
	// 			"  - 성공 시 200(OK)\n"
	// )
	// public ResponseEntity<BaseResponse<Void>> increaseStamp(PrincipalDetails pd){
	// 	temporaryQrService.increaseStamp(pd.getId());
	// 	return ResponseEntity.ok(BaseResponse.onSuccessWithoutData(SuccessStatus._OK));
	// }
}
