package com.assu.server.domain.store.service;


import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import com.assu.server.domain.store.dto.StoreResponseDTO;
import com.assu.server.domain.store.dto.TodayBestResponseDTO;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.certification.repository.QRCertificationRepository;
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
    private final QRCertificationRepository qrCertificationRepository;

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
            return new StoreResponseDTO.WeeklyRankResponseDTO(null, 0L);
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

    @Override
    @Transactional
    public StoreResponseDTO.StampRankingListDTO getStampRanking() {
        List<QRCertificationRepository.StampRankingRow> rows = qrCertificationRepository.findDailyStampRanking();

        List<StoreResponseDTO.StampRankingDTO> rankings = rows.stream()
                .map(row -> new StoreResponseDTO.StampRankingDTO(
                        row.getStoreId(),
                        row.getStoreName(),
                        row.getStampCount()))
                .collect(Collectors.toList());

        return new StoreResponseDTO.StampRankingListDTO(rankings);
    }
}
