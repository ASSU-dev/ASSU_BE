package com.assu.server.domain.store.repository;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.student.entity.PartnershipUsage;
import com.assu.server.domain.student.repository.PartnershipUsageRepository;
import com.assu.server.support.CommonMockConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class StoreRepositoryWeeklyRankTest {

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PartnershipUsageRepository partnershipUsageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findGlobalWeeklyRankForStore_whenOtherStoreHasMoreUsages_thenRankIsBelowFirst() {
        // given
        Store busyStore = createStore("주간순위 많은 가게");
        Store quietStore = createStore("주간순위 적은 가게");
        Store unusedStore = createStore("주간순위 없는 가게");
        createUsages(busyStore, 3);
        createUsages(quietStore, 1);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.minusDays(1);
        LocalDateTime weekEnd = now.plusDays(1);

        // when
        StoreRepository.GlobalWeeklyRankRow busy =
                storeRepository.findGlobalWeeklyRankForStore(busyStore.getId(), weekStart, weekEnd).get(0);
        StoreRepository.GlobalWeeklyRankRow quiet =
                storeRepository.findGlobalWeeklyRankForStore(quietStore.getId(), weekStart, weekEnd).get(0);
        StoreRepository.GlobalWeeklyRankRow unused =
                storeRepository.findGlobalWeeklyRankForStore(unusedStore.getId(), weekStart, weekEnd).get(0);

        // then
        assertThat(busy.getUsageCount()).isEqualTo(3L);
        assertThat(quiet.getUsageCount()).isEqualTo(1L);
        assertThat(unused.getUsageCount()).isZero();
        assertThat(quiet.getStoreRank()).isGreaterThan(1L).isGreaterThan(busy.getStoreRank());
        assertThat(unused.getStoreRank()).isGreaterThan(quiet.getStoreRank());
        assertThat(quiet.getStoreId()).isEqualTo(quietStore.getId());
        assertThat(quiet.getStoreName()).isEqualTo("주간순위 적은 가게");
    }

    @Test
    void findGlobalWeeklyRankForStore_whenUsageIsOutsideRange_thenNotCounted() {
        // given
        Store store = createStore("주간순위 지난주 가게");
        createUsages(store, 2);
        LocalDateTime now = LocalDateTime.now();

        // when
        StoreRepository.GlobalWeeklyRankRow row =
                storeRepository.findGlobalWeeklyRankForStore(store.getId(), now.plusDays(1), now.plusDays(8)).get(0);

        // then
        assertThat(row.getUsageCount()).isZero();
    }

    @Test
    void findGlobalWeeklyTrendLast6Weeks_whenUsagesDifferByWeek_thenRanksEachWeekSeparately() {
        // given
        Store thisWeekLeader = createStore("추세 이번주 1등 가게");
        Store lastWeekLeader = createStore("추세 지난주 1등 가게");
        createUsages(thisWeekLeader, 3);
        createUsages(lastWeekLeader, 1);
        LocalDateTime now = LocalDateTime.now();
        moveCreatedAt(createUsages(lastWeekLeader, 2), now.minusDays(7));
        LocalDateTime rangeEnd = now.plusDays(1);

        // when
        List<StoreRepository.GlobalWeeklyRankRow> thisWeekLeaderRows = findTrend(thisWeekLeader, rangeEnd);
        List<StoreRepository.GlobalWeeklyRankRow> lastWeekLeaderRows = findTrend(lastWeekLeader, rangeEnd);

        // then
        assertThat(thisWeekLeaderRows).hasSize(6);
        assertThat(lastWeekLeaderRows).hasSize(6);
        assertThat(thisWeekLeaderRows).extracting(StoreRepository.GlobalWeeklyRankRow::getUsageCount)
                .containsExactly(0L, 0L, 0L, 0L, 0L, 3L);
        assertThat(lastWeekLeaderRows).extracting(StoreRepository.GlobalWeeklyRankRow::getUsageCount)
                .containsExactly(0L, 0L, 0L, 0L, 2L, 1L);
        assertThat(lastWeekLeaderRows.get(5).getStoreRank())
                .isGreaterThan(1L)
                .isGreaterThan(thisWeekLeaderRows.get(5).getStoreRank());
        assertThat(lastWeekLeaderRows.get(4).getStoreRank())
                .isLessThan(thisWeekLeaderRows.get(4).getStoreRank());
    }

    private List<StoreRepository.GlobalWeeklyRankRow> findTrend(Store store, LocalDateTime rangeEnd) {
        return storeRepository.findGlobalWeeklyTrendLast6Weeks(
                store.getId(),
                rangeEnd.minusWeeks(6),
                rangeEnd.minusWeeks(5),
                rangeEnd.minusWeeks(4),
                rangeEnd.minusWeeks(3),
                rangeEnd.minusWeeks(2),
                rangeEnd.minusWeeks(1),
                rangeEnd);
    }

    private Store createStore(String name) {
        return storeRepository.save(Store.builder()
                .rate(0)
                .isActivate(ActivationStatus.ACTIVE)
                .name(name)
                .address("서울특별시 동작구 상도로 369")
                .detailAddress("1층")
                .latitude(37.0)
                .longitude(127.0)
                .build());
    }

    private List<PartnershipUsage> createUsages(Store store, int count) {
        Paper paper = paperRepository.save(Paper.builder()
                .store(store)
                .isActivated(ActivationStatus.ACTIVE)
                .build());
        List<PartnershipUsage> usages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            usages.add(PartnershipUsage.builder().paperId(paper.getId()).build());
        }
        return partnershipUsageRepository.saveAllAndFlush(usages);
    }

    private void moveCreatedAt(List<PartnershipUsage> usages, LocalDateTime createdAt) {
        for (PartnershipUsage usage : usages) {
            entityManager.createNativeQuery("UPDATE partnership_usage SET created_at = :createdAt WHERE id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", usage.getId())
                    .executeUpdate();
        }
    }
}
