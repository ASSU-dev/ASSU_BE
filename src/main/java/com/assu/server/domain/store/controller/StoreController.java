package com.assu.server.domain.store.controller;

import java.awt.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.assu.server.domain.store.dto.StoreResponseDTO;
import com.assu.server.domain.store.dto.TodayBestResponseDTO;
import com.assu.server.domain.store.service.StoreService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.assu.server.global.util.PrincipalDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
@RestController
@RequiredArgsConstructor
@Tag(name = "Store", description = "가게 관련 API")
@RequestMapping("/store")
public class StoreController {

    private final StoreService storeService;

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
