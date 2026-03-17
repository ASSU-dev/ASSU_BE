package com.assu.server.domain.store.controller;

import java.awt.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.partnership.dto.PaperResponseDTO;
import com.assu.server.domain.partnership.service.PaperQueryService;
import com.assu.server.domain.store.dto.StoreResponseDTO;
import com.assu.server.domain.store.dto.TodayBestResponseDTO;
import com.assu.server.domain.store.service.StoreService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.assu.server.global.util.PrincipalDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
@RestController
@RequiredArgsConstructor
@Tag(name = "Store", description = "가게 API")
@RequestMapping("/store")
public class StoreController {

    private final StoreService storeService;
	private final PaperQueryService paperQueryService;

	@GetMapping("/best")
	@Operation(
		summary = "오늘의 인기 매장 조회 API",
		description =
			"# [v1.0 (2025-12-23)](https://clumsy-seeder-416.notion.site/Today-22b1197c19ed80aebfc3e6b337d02ece?source=copy_link)\n" +
				"- 오늘 기준 인기 매장 목록을 조회합니다.\n" +
				"- 로그인 필요 없음\n" +
				"\n**Response:**\n" +
				"  - `bestStores` (List<String>): 오늘 가장 인기 있는 매장 이름 목록"
	)
	public ResponseEntity<BaseResponse<TodayBestResponseDTO>> getTodayBestStore() {
		TodayBestResponseDTO result = storeService.getTodayBestStore();
		return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus.BEST_STORE_SUCCESS, result));
	}

	@GetMapping("/{storeId}/papers")
	@Operation(
		summary = "제휴 컨텐츠 조회 API",
		description = "# [v1.0 (2026-02-14)](https://clumsy-seeder-416.notion.site/2361197c19ed8019b8b8cb054cd3135b?source=copy_link)\n" +
			"- 유저가 속한 단과대 및 학부의 `admin_id`와 요청한 `store_id`를 매칭하여 적용 가능한 제휴 컨텐츠를 조회합니다.\n" +
			"- QR 코드 스캔 후 유저에게 실제로 보여줄 혜택(Paper) 목록을 가져올 때 사용됩니다.\n\n" +
			"**Path Variable:**\n" +
			"  - `storeId` (Long, required): QR에서 추출한 제휴 매장 ID\n\n" +
			"**Query Parameters:**\n" +
			"  - (없음) - 유저 정보는 토큰(@AuthenticationPrincipal)을 통해 식별합니다.\n\n" +
			"**Response (PaperResponseDTO):**\n" +
			"  - `storeId` (Long): 매장 고유 ID\n" +
			"  - `storeName` (String): 매장 이름\n" +
			"  - `partnershipContents` (List): 제휴 컨텐츠 상세 목록\n" +
			"    - `adminId` (Long): 혜택 제공자 ID\n" +
			"    - `adminName` (String): 혜택 제공자 이름 (예: OO대학 학생회)\n" +
			"    - `paperContent` (String): 제휴 혜택 상세 설명\n" +
			"    - `contentId` (Long): 컨텐츠 고유 ID\n" +
			"    - `goods` (List<String>): 제공되는 상품/서비스 리스트\n" +
			"    - `people` (Integer): 사용 가능 인원\n" +
			"    - `cost` (Long): 유저 부담 금액 (0이면 무료)\n\n" +
			"**Error Cases:**\n" +
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

	@GetMapping("/stamp-ranking")
	@Operation(
		summary = "스탬프 기준 인기 매장 랭킹 조회 API",
		description =
			"# [v1.0 (2026-02-09)](https://clumsy-seeder-416.notion.site/API-3001197c19ed806a99a8fa3e795aba8e?source=copy_link)\n" +
				"- 하루 동안 스탬프가 많이 적립된 매장 순위를 조회합니다.\n" +
				"- 최대 10개까지 반환\n" +
				"- 로그인 필요 없음\n" +
				"\n**Response:**\n" +
				"  - `rankings` (List): 매장 랭킹 목록\n" +
				"  - `storeId` (Long): 매장 ID\n" +
				"  - `storeName` (String): 매장 이름\n" +
				"  - `stampCount` (Long): 스탬프 적립 횟수"
	)
	public ResponseEntity<BaseResponse<StoreResponseDTO.StampRankingListDTO>> getStampRanking() {
		StoreResponseDTO.StampRankingListDTO result = storeService.getStampRanking();
		return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus._OK, result));
	}


	@Operation(
		summary = "내 가게 순위 조회 API",
		description = "# [v1.0 (2025-12-23)](https://www.notion.so/내-가게-순위-API_문서)\n" +
			"- partnerId로 접근 가능합니다.\n" +
			"- 로그인된 파트너만 조회 가능\n\n" +
			"**Response:**\n" +
			"  - `rank` (Long): 그 주 순위 (1부터)\n" +
			"  - `usageCount` (Long): 그 주 사용 건수"
	)
    @GetMapping("/ranking")
    public ResponseEntity<BaseResponse<StoreResponseDTO.WeeklyRankResponseDTO>> getWeeklyRank(
            @AuthenticationPrincipal PrincipalDetails pd) {
        return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus._OK, storeService.getWeeklyRank(pd.getId())));
    }

    @Operation(
            summary = "내 가게 순위 6주치 조회 API",
            description = "partnerId로 접근해주세요"
    )
    @GetMapping("/ranking/weekly")
    public BaseResponse<List<StoreResponseDTO.WeeklyRankResponseDTO>> getWeeklyRankByPartnerId(
            @AuthenticationPrincipal PrincipalDetails pd
    ){
        return BaseResponse.onSuccess(SuccessStatus._OK, storeService.getListWeeklyRank(pd.getId()).items());
    }


}
