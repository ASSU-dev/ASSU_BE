package com.assu.server.domain.partnership.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.partnership.dto.PaperResponseDTO;
import com.assu.server.domain.partnership.service.PaperQueryService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

@RestController
@Tag(name = "Paper", description = "제휴 제안서 조회 api")
@RequiredArgsConstructor
public class PaperController {

	private final PaperQueryService paperQueryService;

	@GetMapping("/store/{storeId}/papers")
	@Operation(
		summary = "유저에게 적용 가능한 제휴 컨텐츠 조회 API",
		description = "# [v1.0 (2026-02-14)](https://clumsy-seeder-416.notion.site/2361197c19ed8019b8b8cb054cd3135b?source=copy_link)\n" +
			"- 유저가 속한 단과대 및 학부의 `admin_id`와 요청한 `store_id`를 매칭하여 적용 가능한 제휴 컨텐츠를 조회합니다.\n" +
			"- QR 코드 스캔 후 유저에게 실제로 보여줄 혜택(Paper) 목록을 가져올 때 사용됩니다.\n\n" +
			"**Path Variable:**\n" +
			"  - `storeId` (Long, required): QR에서 추출한 제휴 매장 ID\n\n" +
			"**Query Parameters:**\n" +
			"  - (없음) - 유저 정보는 토큰(@AuthenticationPrincipal)을 통해 식별합니다.\n\n" +
			"**Response:**\n" +
			"  - 성공: 200 OK, `isSuccess=true`, `result=PaperResponseDTO` (제휴 컨텐츠 상세 정보)\n" +
			"  - 실패: \n" +
			"    - 404 NOT_FOUND: 해당 매장을 찾을 수 없거나 유효한 제휴 컨텐츠가 없는 경우\n" +
			"    - 403 FORBIDDEN: 유저 권한이 없거나 인증에 실패한 경우"
	)
	@Parameters({
		@Parameter(name = "storeId", description = "QR에서 추출한 storeId를 입력해주세요")
	})
	public ResponseEntity<BaseResponse<PaperResponseDTO>> getStorePaperContent(@PathVariable Long storeId,
		@AuthenticationPrincipal PrincipalDetails pd
	) {
		PaperResponseDTO result = paperQueryService.getStorePaperContent(storeId, pd.getMember());

		return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus.PAPER_STORE_HISTORY_SUCCESS, result));
	}

}