package com.assu.server.domain.partner.repository;

import com.assu.server.domain.partner.entity.Partner;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    boolean existsByPhoneNum(String phoneNum);

    // 미제휴 제휴업체 수 조회
    @Query("""
        SELECT COUNT(p)
        FROM Partner p
        WHERE NOT EXISTS (
            SELECT 1 FROM Paper pa
            WHERE pa.partner = p
              AND pa.admin.id = :adminId
              AND pa.isActivated = com.assu.server.domain.common.enums.ActivationStatus.ACTIVE
        )
        """)
    long countUnpartneredActiveByAdmin(@Param("adminId") Long adminId);

    // 미제휴 제휴업체 랜덤 오프셋 조회
    @Query("""
        SELECT p
        FROM Partner p
        WHERE NOT EXISTS (
            SELECT 1 FROM Paper pa
            WHERE pa.partner = p
              AND pa.admin.id = :adminId
              AND pa.isActivated = com.assu.server.domain.common.enums.ActivationStatus.ACTIVE
        )
        """)
    List<Partner> findUnpartneredActiveByAdminWithOffset(@Param("adminId") Long adminId, Pageable pageable);

    // 반경 내 제휴업체 조회
    @Query("""
        SELECT DISTINCT p
        FROM Partner p
        LEFT JOIN FETCH p.member
        WHERE p.point IS NOT NULL
          AND ST_Contains(ST_GeomFromText(:wkt, 4326), p.point) = true
        """)
    List<Partner> findAllWithinViewportWithMember(@Param("wkt") String wkt);

    // 키워드 검색
    @Query("""
        SELECT DISTINCT p
        FROM Partner p
        LEFT JOIN FETCH p.member
        WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        """)
    List<Partner> searchPartnerByKeywordWithMember(
            @Param("keyword") String keyword
    );
}