package com.assu.server.domain.partner.repository;

import com.assu.server.domain.partner.entity.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    // 현재 admin과 'ACTIVE' 상태로 제휴 중인 partner를 제외한 후보 수
    @Query(value = """
        SELECT COUNT(*)
        FROM partner p
        LEFT JOIN paper pa
          ON pa.partner_id = p.id
         AND pa.admin_id = :adminId
         AND pa.is_activated = 'ACTIVE'
        WHERE pa.id IS NULL
        """, nativeQuery = true)
    long countUnpartneredActiveByAdmin(@Param("adminId") Long adminId);

    // 위 후보들 중에서 offset 하나만 가져오기 (랜덤 오프셋으로 1건)
    @Query(value = """
        SELECT p.*
        FROM partner p
        LEFT JOIN paper pa
          ON pa.partner_id = p.id
         AND pa.admin_id = :adminId
         AND pa.is_activated = 'ACTIVE'
        WHERE pa.id IS NULL
        LIMIT :offset, 1
        """, nativeQuery = true)
    Partner findUnpartneredActiveByAdminWithOffset(@Param("adminId") Long adminId,
                                                   @Param("offset") int offset);

    @Query(value = """
        SELECT p.*
        FROM partner p
        WHERE p.point IS NOT NULL
          AND ST_Contains(ST_GeomFromText(:wkt, 4326), p.point)
        """, nativeQuery = true)
    List<Partner> findAllWithinViewport(@Param("wkt") String wkt);

    @Query("""
        select distinct p
        from Partner p
        where lower(p.name) like lower(concat('%', :keyword, '%'))
        """)
    List<Partner> searchPartnerByKeyword(
            @Param("keyword") String keyword
    );


}
