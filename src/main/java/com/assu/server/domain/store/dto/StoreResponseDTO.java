package com.assu.server.domain.store.dto;

import java.util.List;

public class StoreResponseDTO {

    public record WeeklyRankResponseDTO(
        Long rank,           // 그 주 순위(1부터)
        Long usageCount      // 그 주 사용 건수
    ) {}

    public record StampRankingDTO(
        Long storeId,
        String storeName,
        Long stampCount
    ) {}

    public record StampRankingListDTO(
        List<StampRankingDTO> rankings
    ) {}

    public record ListWeeklyRankResponseDTO(
        Long storeId,
        String storeName,
        List<WeeklyRankResponseDTO> items // 과거→현재 (6개)
    ) {}

}
