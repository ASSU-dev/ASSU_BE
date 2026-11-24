package com.assu.server.domain.store.service;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import com.assu.server.domain.store.exception.CustomStoreException;
import com.assu.server.infra.s3.AmazonS3Manager;
import org.springframework.stereotype.Service;
import com.assu.server.domain.store.dto.StoreResponseDTO;
import com.assu.server.domain.store.dto.TodayBestResponseDTO;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.certification.repository.QRCertificationRepository;
import com.assu.server.domain.student.repository.PartnershipUsageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.converter.StoreConverter;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {
    private final StoreRepository storeRepository;
    private final PartnerRepository partnerRepository;
	private final PartnershipUsageRepository partnershipUsageRepository;
    private final QRCertificationRepository qrCertificationRepository;
    private final AmazonS3Manager amazonS3Manager;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Override
    @Transactional
    public StoreResponseDTO.GetStoreDetailsDTO getStoreDetails(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new CustomStoreException(ErrorStatus.NO_SUCH_STORE));

        Partner partner = store.getPartner();
        String profileUrl = null;
        if (partner != null && partner.getMember() != null) {
            String key = partner.getMember().getProfileUrl();
            if (key != null && !key.isBlank()) {
                profileUrl = amazonS3Manager.generatePresignedUrl(key);
            }
        }

        return new StoreResponseDTO.GetStoreDetailsDTO(
                store.getId(),
                store.getName(),
                store.getAddress(),
                store.getDetailAddress(),
                store.getLatitude(),
                store.getLongitude(),
                store.getStoreCategory() != null ? store.getStoreCategory().name() : null,
                store.getRate(),
                partner != null ? partner.getPhoneNum() : null,
                partner != null,
                profileUrl,
                store.getLinkType()
        );
    }

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
                .orElseThrow(() -> new CustomStoreException(ErrorStatus.NO_SUCH_STORE));
        Long storeId = store.getId();

        LocalDate weekStart = currentWeekStart();
        List<StoreRepository.GlobalWeeklyRankRow> rows = storeRepository.findGlobalWeeklyRankForStore(
                storeId, toStoredDateTime(weekStart), toStoredDateTime(weekStart.plusWeeks(1)));
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
                .orElseThrow(() -> new CustomStoreException(ErrorStatus.NO_SUCH_STORE));
        Long storeId = store.getId();

        LocalDate oldestWeekStart = currentWeekStart().minusWeeks(5);
        List<StoreRepository.GlobalWeeklyRankRow> rows = storeRepository.findGlobalWeeklyTrendLast6Weeks(
                storeId,
                toStoredDateTime(oldestWeekStart),
                toStoredDateTime(oldestWeekStart.plusWeeks(1)),
                toStoredDateTime(oldestWeekStart.plusWeeks(2)),
                toStoredDateTime(oldestWeekStart.plusWeeks(3)),
                toStoredDateTime(oldestWeekStart.plusWeeks(4)),
                toStoredDateTime(oldestWeekStart.plusWeeks(5)),
                toStoredDateTime(oldestWeekStart.plusWeeks(6)));

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

    private static LocalDate currentWeekStart() {
        return LocalDate.now(KST).with(DayOfWeek.MONDAY);
    }

    // created_at은 JVM 기본 시간대의 LocalDateTime으로 기록되므로, KST 자정을 같은 기준의 값으로 바꿔 비교한다
    private static LocalDateTime toStoredDateTime(LocalDate kstDate) {
        return kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
