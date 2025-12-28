package com.assu.server.domain.store.service;


import java.util.List;
import org.springframework.stereotype.Service;
import com.assu.server.domain.store.dto.StoreResponseDTO;
import com.assu.server.domain.store.dto.TodayBestResponseDTO;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.user.repository.PartnershipUsageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.converter.StoreConverter;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {
    private final StoreRepository storeRepository;
    private final PartnerRepository partnerRepository;
	private final PartnershipUsageRepository partnershipUsageRepository;

	@Override
	@Transactional
	public TodayBestResponseDTO getTodayBestStore() {
		List<String> bestStores = storeRepository.findTodayBestStoreNames();
		return new TodayBestResponseDTO(bestStores);
	}

    @Override
    @Transactional
    public StoreResponseDTO.WeeklyRankResponseDTO getWeeklyRank(Long memberId) {

        Optional<Partner> partner = partnerRepository.findById(memberId);
        Store store = storeRepository.findByPartner(partner.orElse(null))
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STORE));
        Long storeId = store.getId();

        List<StoreRepository.GlobalWeeklyRankRow> rows = storeRepository.findGlobalWeeklyRankForStore(storeId);
        if (rows.isEmpty()) {
            // 데이터가 없을 때 기본값 반환(필요 시 예외로 변경)
            return StoreResponseDTO.WeeklyRankResponseDTO.builder()
                    .rank(null)
                    .usageCount(0L)
                    .build();
        }
        return StoreConverter.weeklyRankResponseDTO(rows.get(0));
    }

    @Override
    @Transactional
    public StoreResponseDTO.ListWeeklyRankResponseDTO getListWeeklyRank(Long memberId) {

        Optional<Partner> partner = partnerRepository.findById(memberId);
        Store store = storeRepository.findByPartner(partner.orElse(null))
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STORE));
        Long storeId = store.getId();

        List<StoreRepository.GlobalWeeklyRankRow> rows = storeRepository.findGlobalWeeklyTrendLast6Weeks(storeId);

        String storeName = rows.isEmpty() ? null : rows.get(0).getStoreName();
        return StoreConverter.listWeeklyRankResponseDTO(storeId, storeName, rows);

    }
}
