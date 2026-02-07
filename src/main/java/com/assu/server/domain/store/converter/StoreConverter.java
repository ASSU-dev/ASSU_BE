package com.assu.server.domain.store.converter;

import com.assu.server.domain.store.dto.StoreResponseDTO;
import com.assu.server.domain.store.repository.StoreRepository;

import java.util.List;
import java.util.stream.Collectors;

public class StoreConverter {
    // 단건(이번 주) 변환: Row -> Response
    public static StoreResponseDTO.WeeklyRankResponseDTO weeklyRankResponseDTO(StoreRepository.GlobalWeeklyRankRow r) {
        return new StoreResponseDTO.WeeklyRankResponseDTO(
                r.getStoreRank(),
                r.getUsageCount()
        );
    }

    // 리스트 아이템 변환용: Row -> WeeklyRankResponseDTO
    public static StoreResponseDTO.WeeklyRankResponseDTO weeklyRankItem(StoreRepository.GlobalWeeklyRankRow r) {
        return new StoreResponseDTO.WeeklyRankResponseDTO(
                r.getStoreRank(),
                r.getUsageCount()
        );
    }

    // 리스트 래핑: storeId, storeName, items를 받아 최종 DTO 조립
    public static StoreResponseDTO.ListWeeklyRankResponseDTO listWeeklyRankResponseDTO(
            Long storeId, String storeName, List<StoreRepository.GlobalWeeklyRankRow> rows
    ) {
        List<StoreResponseDTO.WeeklyRankResponseDTO> items = rows.stream()
                .map(StoreConverter::weeklyRankItem)
                .collect(Collectors.toList());

        return new StoreResponseDTO.ListWeeklyRankResponseDTO(
                storeId,
                storeName,
                items
        );
    }
}
