package com.assu.server.domain.qr.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.qr.dto.TemporaryQrRequestDTO;
import com.assu.server.domain.qr.dto.TemporaryQrResponseDTO;
import com.assu.server.domain.qr.service.TemporaryQrService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import okhttp3.Response;

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
			"# # [v1.0 (2026-02-14)](https://clumsy-seeder-416.notion.site/QR-3071197c19ed8079aeb9d0555b9e899f?source=copy_link)\n" +
				"- 임시 QR 적립 데이터를 저장합니다.\n" +
				"- 요청 바디(JSON)를 통해 QR 적립 대상 정보를 전달합니다.\n" +
				"\n**Request Body:**\n" +
				"  - `storeId` (Long, required): 매장 ID\n" +
				"  - `adminId` (Long, required): 학생회(관리자) ID\n" +
				"  - `sort` (Enum, required): 어떻게 적립되었는지 REVIEW/SUGGEST 중 하나 입력\n" +
				"\n**Response:**\n" +
				"  - 성공 시 200(OK)\n"
	)
	public ResponseEntity<BaseResponse<Void>> insertQrData(@RequestBody TemporaryQrRequestDTO dto,
		@AuthenticationPrincipal PrincipalDetails pd){
		temporaryQrService.insertData(dto, pd.getMember());
		return ResponseEntity.ok(BaseResponse.onSuccessWithoutData(SuccessStatus._OK));
	}

	@GetMapping("/mydata")
	@Operation(
		summary = "내 QR 적립 데이터 조회 API",
		description =
			"# # [v1.0 (2026-02-15)](https://clumsy-seeder-416.notion.site/QR-3081197c19ed805e9f6bd012dafe6f01?source=copy_link)\n" +
				"- 로그인한 사용자의 임시 QR 적립 데이터를 조회합니다.\n" +
				"- 인증된 사용자의 토큰을 통해 자동으로 데이터를 가져옵니다.\n" +
				"\n**Request:**\n" +
				"  - 별도의 파라미터 없음 (토큰 기반 인증)\n" +
				"\n**Response:**\n" +
				"  - `adminName` (String): 학생회(관리자) 이름- 만약 앱 리뷰인 경우 \"\" 와 같이 들어가게 됨\n" +
				"  - `sort` (Enum): 적립 방식 (REVIEW/SUGGEST)\n" +
				"\n**Response Example:**\n" +
				"```json\n" +
				"[\n" +
				"  {\n" +
				"    \"adminName\": \"총학생회\",\n" +
				"    \"sort\": \"SUGGEST\"\n" +
				"  },\n" +
				"  {\n" +
				"    \"adminName\": \"\",\n" +
				"    \"sort\": \"REVIEW\"\n" +
				"  }\n" +
				"]\n" +
				"```\n" +
				"\n**Status:**\n" +
				"  - 성공 시 200(OK)\n"
	)

	public ResponseEntity<BaseResponse<List<TemporaryQrResponseDTO>>> getMyStampData(
		@AuthenticationPrincipal PrincipalDetails pd){
		return ResponseEntity.ok(BaseResponse
			.onSuccess(SuccessStatus.TEMPORARY_QR_DATA_SUCCESS,temporaryQrService.getTemporaryQrData(pd.getMember())));
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
