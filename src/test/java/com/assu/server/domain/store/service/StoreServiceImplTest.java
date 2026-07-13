package com.assu.server.domain.store.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.certification.repository.QRCertificationRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.dto.StoreResponseDTO;
import com.assu.server.domain.store.dto.TodayBestResponseDTO;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.exception.CustomStoreException;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.repository.PartnershipUsageRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

@ExtendWith(MockitoExtension.class)
class StoreServiceImplTest {

	@InjectMocks
	private StoreServiceImpl storeService;

	@Mock
	private StoreRepository storeRepository;

	@Mock
	private PartnerRepository partnerRepository;

	@Mock
	private PartnershipUsageRepository partnershipUsageRepository;

	@Mock
	private QRCertificationRepository qrCertificationRepository;

	private static final Long PARTNER_ID = 20L;

	@Test
	@DisplayName("오늘의 베스트 매장 이름 목록을 그대로 반환한다")
	void getTodayBestStore_ReturnsStoreNames() {
		// 1. Given
		when(storeRepository.findTodayBestStoreNames())
			.thenReturn(List.of("역전할머니 맥주", "숭실분식"));

		// 2. When
		TodayBestResponseDTO response = storeService.getTodayBestStore();

		// 3. Then
		assertNotNull(response);
		verify(storeRepository, times(1)).findTodayBestStoreNames();
	}

	@Test
	@DisplayName("파트너에게 연결된 매장이 없으면 주간 순위 조회 시 NO_SUCH_STORE 예외가 발생한다")
	void getWeeklyRank_StoreNotFound_ThrowsException() {
		// 1. Given
		Partner partner = Partner.builder().id(PARTNER_ID).name("역전할머니 맥주").isPhoneVerified(false).build();
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
		when(storeRepository.findByPartner(partner)).thenReturn(Optional.empty());

		// 2. When
		CustomStoreException exception = assertThrows(CustomStoreException.class,
			() -> storeService.getWeeklyRank(PARTNER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_STORE, exception.getCode());
	}

	@Test
	@DisplayName("이번 주 이용 데이터가 없으면 순위 null·이용 0건의 기본값을 반환한다")
	void getWeeklyRank_NoData_ReturnsDefault() {
		// 1. Given
		Partner partner = Partner.builder().id(PARTNER_ID).name("역전할머니 맥주").isPhoneVerified(false).build();
		Store store = Store.builder().id(500L).partner(partner).build();
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
		when(storeRepository.findByPartner(partner)).thenReturn(Optional.of(store));
		when(storeRepository.findGlobalWeeklyRankForStore(500L)).thenReturn(List.of());

		// 2. When
		StoreResponseDTO.WeeklyRankResponseDTO response = storeService.getWeeklyRank(PARTNER_ID);

		// 3. Then
		assertNull(response.rank());
		assertEquals(0L, response.usageCount());
	}

	@Test
	@DisplayName("일별 스탬프 랭킹을 매장 정보와 함께 DTO 리스트로 변환한다")
	void getStampRanking_MapsRowsToDto() {
		// 1. Given
		QRCertificationRepository.StampRankingRow row1 = mock(QRCertificationRepository.StampRankingRow.class);
		when(row1.getStoreId()).thenReturn(500L);
		when(row1.getStoreName()).thenReturn("역전할머니 맥주");
		when(row1.getStampCount()).thenReturn(12L);

		QRCertificationRepository.StampRankingRow row2 = mock(QRCertificationRepository.StampRankingRow.class);
		when(row2.getStoreId()).thenReturn(501L);
		when(row2.getStoreName()).thenReturn("숭실분식");
		when(row2.getStampCount()).thenReturn(7L);

		when(qrCertificationRepository.findDailyStampRanking()).thenReturn(List.of(row1, row2));

		// 2. When
		StoreResponseDTO.StampRankingListDTO response = storeService.getStampRanking();

		// 3. Then
		assertEquals(2, response.rankings().size());
		assertEquals(500L, response.rankings().get(0).storeId());
		assertEquals("역전할머니 맥주", response.rankings().get(0).storeName());
		assertEquals(12L, response.rankings().get(0).stampCount());
		assertEquals(7L, response.rankings().get(1).stampCount());
	}
}
