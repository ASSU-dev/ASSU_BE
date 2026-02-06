package com.assu.server.domain.store.repository;


import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.partner.entity.Partner;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store,Long> {

    Optional<Store> findByPartner(Partner partner);

    Optional<Store> findByNameAndAddressAndDetailAddress(String name, String address, String detailAddress);

    // [이번 주] 전체 스토어 중 특정 storeId의 주간 순위/건수 1건 (ACTIVE만)
    @Query(value = """
        WITH w AS (
          SELECT DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) AS week_start
        ),
        per_store AS (
          SELECT
            s.id                                        AS storeId,
            s.name                                      AS storeName,
            CAST(COALESCE(COUNT(pu.id), 0) AS UNSIGNED) AS usageCount,
            (SELECT week_start FROM w)                  AS weekStart
          FROM store s
          LEFT JOIN paper p ON p.store_id = s.id AND p.is_activated = 'ACTIVE'
          LEFT JOIN partnership_usage pu
                 ON pu.paper_id = p.id
                AND pu.created_at >= (SELECT week_start FROM w)
                AND pu.created_at <  (SELECT week_start FROM w) + INTERVAL 7 DAY
          GROUP BY s.id, s.name
        )
        SELECT
          ps.weekStart  AS weekStart,
          ps.storeId    AS storeId,
          ps.storeName  AS storeName,
          ps.usageCount AS usageCount,
          CAST(
            DENSE_RANK() OVER (ORDER BY ps.usageCount DESC, ps.storeId ASC)
            AS UNSIGNED
          )            AS storeRank
        FROM per_store ps
        WHERE ps.storeId = :storeId
        """, nativeQuery = true)
    List<GlobalWeeklyRankRow> findGlobalWeeklyRankForStore(@Param("storeId") Long storeId);

    interface GlobalWeeklyRankRow {
        LocalDate getWeekStart();
        Long getStoreId();
        String getStoreName();
        Long getUsageCount();
        Long getStoreRank();
    }

    // [최근 6주] 전체 스토어 기준, 특정 storeId의 주간 순위/건수(월요일 시작) 추세 (ACTIVE만)
    @Query(value = """
        WITH RECURSIVE weeks AS (
          SELECT DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) AS week_start
          UNION ALL
          SELECT week_start - INTERVAL 7 DAY FROM weeks
          WHERE week_start > DATE_SUB(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 5 WEEK)
        ),
        per_store_week AS (
          SELECT
            w.week_start                                  AS weekStart,
            s.id                                          AS storeId,
            s.name                                        AS storeName,
            CAST(COALESCE(COUNT(pu.id), 0) AS UNSIGNED)   AS usageCount
          FROM weeks w
          JOIN store s ON 1=1
          LEFT JOIN paper p ON p.store_id = s.id AND p.is_activated = 'ACTIVE'
          LEFT JOIN partnership_usage pu
                 ON pu.paper_id = p.id
                AND pu.created_at >= w.week_start
                AND pu.created_at <  w.week_start + INTERVAL 7 DAY
          GROUP BY w.week_start, s.id, s.name
        )
        SELECT
          pw.weekStart  AS weekStart,
          pw.storeId    AS storeId,
          pw.storeName  AS storeName,
          pw.usageCount AS usageCount,
          CAST(
            DENSE_RANK() OVER (
              PARTITION BY pw.weekStart
              ORDER BY pw.usageCount DESC, pw.storeId ASC
            ) AS UNSIGNED
          )            AS storeRank
        FROM per_store_week pw
        WHERE pw.storeId = :storeId
        ORDER BY pw.weekStart ASC
        """, nativeQuery = true)
    List<GlobalWeeklyRankRow> findGlobalWeeklyTrendLast6Weeks(@Param("storeId") Long storeId);

    @Query("""
        SELECT s FROM Store s
        WHERE s.address = :address
          AND ((:detail IS NULL AND s.detailAddress IS NULL) OR s.detailAddress = :detail)
    """)
    Optional<Store> findBySameAddress(
            @Param("address") String address,
            @Param("detail") String detail
    );

    @Query(value = """
        SELECT s.*
        FROM store s
        WHERE s.point IS NOT NULL
          AND ST_Contains(ST_GeomFromText(:wkt, 4326), s.point)
        """, nativeQuery = true)
    List<Store> findAllWithinViewport(@Param("wkt") String wkt);

    @Query("""
        SELECT DISTINCT s
        FROM Store s
        LEFT JOIN FETCH s.partner p
        LEFT JOIN FETCH p.member
        WHERE s.point IS NOT NULL
          AND function('ST_Contains', function('ST_GeomFromText', :wkt, 4326), s.point) = true
        """)
    List<Store> findAllWithinViewportWithPartner(@Param("wkt") String wkt);

    List<Store> findByNameContainingIgnoreCaseOrderByIdDesc(String name);

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