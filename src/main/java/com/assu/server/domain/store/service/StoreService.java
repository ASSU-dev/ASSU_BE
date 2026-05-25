package com.assu.server.domain.store.service;
import com.assu.server.domain.store.dto.StoreResponseDTO;
import com.assu.server.domain.store.dto.TodayBestResponseDTO;

public interface StoreService {
	TodayBestResponseDTO getTodayBestStore();
    StoreResponseDTO.WeeklyRankResponseDTO getWeeklyRank(Long memberId);
    StoreResponseDTO.ListWeeklyRankResponseDTO getListWeeklyRank(Long memberId);
    StoreResponseDTO.StampRankingListDTO getStampRanking();
}
