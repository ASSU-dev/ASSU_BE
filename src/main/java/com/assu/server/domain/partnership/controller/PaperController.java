package com.assu.server.domain.partnership.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
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
@Tag(name = "제휴 관련 내용 '조회' api", description = "상세 설명")
@RequiredArgsConstructor
public class PaperController {

	private final PaperQueryService paperQueryService;

	@GetMapping("/store/{storeId}/papers")
	@Operation(summary = "유저에게 적용 가능한 제휴 컨텐츠 조회", description = "유저가 속한 단과대, 학부 admin_id과 store_id 를 가진 제휴 컨텐츠 제공")
	@Parameters({
		@Parameter(name = "storeId", description = "QR에서 추출한 storeId를 입력해주세요")
	})
	public ResponseEntity<BaseResponse<PaperResponseDTO.partnershipContent>> getStorePaperContent(@PathVariable Long storeId,
		@AuthenticationPrincipal PrincipalDetails pd
	) {
		PaperResponseDTO.partnershipContent result = paperQueryService.getStorePaperContent(storeId, pd.getMember());

		return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus.PAPER_STORE_HISTORY_SUCCESS, result));
	}

}