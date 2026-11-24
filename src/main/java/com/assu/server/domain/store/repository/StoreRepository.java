package com.assu.server.domain.store.repository;
import java.util.Optional;

import com.assu.server.domain.store.entity.enums.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.partner.entity.Partner;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store,Long> {

    @Query("""
        SELECT s FROM Store s
        LEFT JOIN s.partner p
        LEFT JOIN p.member m
        WHERE m.deletedAt IS NULL
    """)
    List<Store> findAllExcludingWithdrawnPartner();

    @Query("""
        SELECT DISTINCT s FROM UserPaper up
        JOIN up.paper p
        JOIN p.store s
        LEFT JOIN FETCH s.partner partner
        LEFT JOIN FETCH partner.member
        WHERE up.student.id = :studentId
          AND p.isActivated = com.assu.server.domain.common.enums.ActivationStatus.ACTIVE
    """)
    List<Store> findDistinctActiveStoresByStudentId(@Param("studentId") Long studentId);

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
        SELECT DISTINCT s
        FROM Store s
        LEFT JOIN FETCH s.partner p
        LEFT JOIN FETCH p.member
        WHERE s.point IS NOT NULL
          AND function('ST_Contains', function('ST_GeomFromText', :wkt, 4326), s.point) = true
        """)
    List<Store> findAllWithinViewportWithPartner(
            @Param("wkt") String wkt
    );

    @Query("""
        SELECT DISTINCT s
        FROM Store s
        LEFT JOIN FETCH s.partner p
        LEFT JOIN FETCH p.member
        WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))
        ORDER BY s.id DESC
        """)
    List<Store> findByNameContainingIgnoreCaseOrderByIdDescWithPartner(@Param("name") String name);
    Optional<Store> findByName(String name);
    Optional<Store> findById(Long id);
    Optional<Store> findByPartnerId(Long partnerId);

    // [오늘] 전체 스토어 중 사용 건수 상위 10개 (ACTIVE만)
    @Query(value = """
        SELECT s.name
        FROM store s
        LEFT JOIN paper p ON p.store_id = s.id AND p.is_activated = 'ACTIVE'
        LEFT JOIN partnership_usage pu 
            ON pu.paper_id = p.id
            AND pu.created_at >= CURDATE()
            AND pu.created_at < CURDATE() + INTERVAL 1 DAY
        GROUP BY s.id, s.name
        HAVING COUNT(pu.id) > 0
        ORDER BY COUNT(pu.id) DESC, s.id ASC
        LIMIT 10
        """, nativeQuery = true)
    List<String> findTodayBestStoreNames();
}
