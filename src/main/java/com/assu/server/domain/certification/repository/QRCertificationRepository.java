package com.assu.server.domain.certification.repository;

import com.assu.server.domain.certification.entity.QRCertification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QRCertificationRepository extends JpaRepository<QRCertification, Long> {

    @Query(value = """
        SELECT s.id AS storeId, s.name AS storeName, COUNT(qr.id) AS stampCount
        FROM qrcertification qr
        JOIN associate_certification ac ON qr.certification_id = ac.id
        JOIN store s ON ac.store_id = s.id
        WHERE qr.is_verified = true
          AND qr.verified_time >= CURDATE()
          AND qr.verified_time < CURDATE() + INTERVAL 1 DAY
        GROUP BY s.id, s.name
        ORDER BY stampCount DESC, s.id ASC
        LIMIT 10
        """, nativeQuery = true)
    List<StampRankingRow> findDailyStampRanking();

    interface StampRankingRow {
        Long getStoreId();
        String getStoreName();
        Long getStampCount();
    }
}
