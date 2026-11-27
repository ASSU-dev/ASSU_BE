package com.assu.server.domain.store.repository;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.partner.entity.Partner;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store,Long> {

    @Query("""
        SELECT s FROM Store s
        LEFT JOIN FETCH s.partner p
        LEFT JOIN FETCH p.member m
        WHERE m.deletedAt IS NULL
        ORDER BY s.rate DESC NULLS LAST, s.id ASC
    """)
    List<Store> findTopRatedExcludingWithdrawnPartner(Pageable pageable);

    @Query("""
        SELECT s FROM Store s
        ORDER BY s.rate DESC NULLS LAST, s.id ASC
    """)
    List<Store> findTopRated(Pageable pageable);

    @Query("""
        SELECT DISTINCT p.store.id FROM UserPaper up
        JOIN up.paper p
        WHERE up.student.id = :studentId
          AND p.store IS NOT NULL
          AND p.isActivated = com.assu.server.domain.common.enums.ActivationStatus.ACTIVE
    """)
    List<Long> findDistinctActiveStoreIdsByStudentId(@Param("studentId") Long studentId);

    @Query("""
        SELECT s FROM Store s
        LEFT JOIN FETCH s.partner partner
        LEFT JOIN FETCH partner.member
        WHERE s.id IN :ids
    """)
    List<Store> findAllWithPartnerByIdIn(@Param("ids") Collection<Long> ids);

    Optional<Store> findByPartner(Partner partner);

    Optional<Store> findByNameAndAddressAndDetailAddress(String name, String address, String detailAddress);

    // 순위는 전체 가게를 대상으로 안쪽에서 매기고, 가게 필터는 바깥에서 건다 (같은 SELECT에서 거르면 항상 1위)
    @Query(value = """
        SELECT
          r.weekStart  AS weekStart,
          r.storeId    AS storeId,
          r.storeName  AS storeName,
          r.usageCount AS usageCount,
          r.storeRank  AS storeRank
        FROM (
          SELECT
            CAST(:weekStart AS DATE)   AS weekStart,
            s.id                       AS storeId,
            s.name                     AS storeName,
            COALESCE(u.usage_count, 0) AS usageCount,
            DENSE_RANK() OVER (ORDER BY COALESCE(u.usage_count, 0) DESC, s.id ASC) AS storeRank
          FROM store s
          LEFT JOIN (
            SELECT p.store_id AS store_id, COUNT(*) AS usage_count
            FROM partnership_usage pu
            JOIN paper p ON p.id = pu.paper_id AND p.is_activated = 'ACTIVE'
            WHERE pu.created_at >= :weekStart AND pu.created_at < :weekEnd
            GROUP BY p.store_id
          ) u ON u.store_id = s.id
        ) r
        WHERE r.storeId = :storeId
        """, nativeQuery = true)
    List<GlobalWeeklyRankRow> findGlobalWeeklyRankForStore(
            @Param("storeId") Long storeId,
            @Param("weekStart") LocalDateTime weekStart,
            @Param("weekEnd") LocalDateTime weekEnd
    );

    interface GlobalWeeklyRankRow {
        LocalDate getWeekStart();
        Long getStoreId();
        String getStoreName();
        Long getUsageCount();
        Long getStoreRank();
    }

    // 6개 주(week0Start ~ rangeEnd, 7일 간격)마다 전체 가게 순위를 매긴 뒤 바깥에서 가게를 거른다
    @Query(value = """
        SELECT
          r.weekStart  AS weekStart,
          r.storeId    AS storeId,
          r.storeName  AS storeName,
          r.usageCount AS usageCount,
          r.storeRank  AS storeRank
        FROM (
          SELECT
            w.week_idx                 AS weekIdx,
            w.week_start               AS weekStart,
            s.id                       AS storeId,
            s.name                     AS storeName,
            COALESCE(u.usage_count, 0) AS usageCount,
            DENSE_RANK() OVER (
              PARTITION BY w.week_idx
              ORDER BY COALESCE(u.usage_count, 0) DESC, s.id ASC
            )                          AS storeRank
          FROM (
            SELECT 0 AS week_idx, CAST(:week0Start AS DATE) AS week_start
            UNION ALL SELECT 1, CAST(:week1Start AS DATE)
            UNION ALL SELECT 2, CAST(:week2Start AS DATE)
            UNION ALL SELECT 3, CAST(:week3Start AS DATE)
            UNION ALL SELECT 4, CAST(:week4Start AS DATE)
            UNION ALL SELECT 5, CAST(:week5Start AS DATE)
          ) w
          CROSS JOIN store s
          LEFT JOIN (
            SELECT b.week_idx AS week_idx, b.store_id AS store_id, COUNT(*) AS usage_count
            FROM (
              SELECT
                CASE
                  WHEN pu.created_at < :week1Start THEN 0
                  WHEN pu.created_at < :week2Start THEN 1
                  WHEN pu.created_at < :week3Start THEN 2
                  WHEN pu.created_at < :week4Start THEN 3
                  WHEN pu.created_at < :week5Start THEN 4
                  ELSE 5
                END        AS week_idx,
                p.store_id AS store_id
              FROM partnership_usage pu
              JOIN paper p ON p.id = pu.paper_id AND p.is_activated = 'ACTIVE'
              WHERE pu.created_at >= :week0Start AND pu.created_at < :rangeEnd
            ) b
            GROUP BY b.week_idx, b.store_id
          ) u ON u.week_idx = w.week_idx AND u.store_id = s.id
        ) r
        WHERE r.storeId = :storeId
        ORDER BY r.weekIdx ASC
        """, nativeQuery = true)
    List<GlobalWeeklyRankRow> findGlobalWeeklyTrendLast6Weeks(
            @Param("storeId") Long storeId,
            @Param("week0Start") LocalDateTime week0Start,
            @Param("week1Start") LocalDateTime week1Start,
            @Param("week2Start") LocalDateTime week2Start,
            @Param("week3Start") LocalDateTime week3Start,
            @Param("week4Start") LocalDateTime week4Start,
            @Param("week5Start") LocalDateTime week5Start,
            @Param("rangeEnd") LocalDateTime rangeEnd
    );

    @Query("""
        SELECT s FROM Store s
        WHERE s.address = :address
          AND ((:detail IS NULL AND s.detailAddress IS NULL) OR s.detailAddress = :detail)
    """)
    Optional<Store> findBySameAddress(
            @Param("address") String address,
            @Param("detail") String detail
    );

    @Query("""
        SELECT s
        FROM Store s
        LEFT JOIN FETCH s.partner p
        LEFT JOIN FETCH p.member
        WHERE s.point IS NOT NULL
          AND function('ST_Contains', function('ST_GeomFromText', :wkt, 4326), s.point) = true
        """)
    List<Store> findAllWithinViewportWithPartner(
            @Param("wkt") String wkt
    );

    Optional<Store> findByName(String name);
    Optional<Store> findById(Long id);
    Optional<Store> findByPartnerId(Long partnerId);

    // [오늘] 전체 스토어 중 사용 건수 상위 10개 (ACTIVE만)
    @Query(value = """
        SELECT s.name
        FROM partnership_usage pu
        JOIN paper p ON p.id = pu.paper_id AND p.is_activated = 'ACTIVE'
        JOIN store s ON s.id = p.store_id
        WHERE pu.created_at >= :dayStart AND pu.created_at < :dayEnd
        GROUP BY s.id, s.name
        ORDER BY COUNT(*) DESC, s.id ASC
        LIMIT 10
        """, nativeQuery = true)
    List<String> findTodayBestStoreNames(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );
}
