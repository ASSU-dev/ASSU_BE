package com.assu.server.domain.admin.repository;

import java.util.List;
import java.util.Optional;

import com.assu.server.domain.admin.entity.Admin;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.assu.server.domain.user.entity.enums.Department;
import com.assu.server.domain.user.entity.enums.Major;
import com.assu.server.domain.user.entity.enums.University;

public interface AdminRepository extends JpaRepository<Admin, Long> {

	@Query("""
		SELECT a FROM Admin a
		WHERE a.university = :university
		  AND (
		    (a.department IS NULL AND a.major IS NULL) OR
		    (a.department = :department AND a.major IS NULL) OR
		    (a.department = :department AND a.major = :major)
		  )
		""")
	List<Admin> findMatchingAdmins(@Param("university") University university,
		@Param("department") Department department,
		@Param("major") Major major);

    // 후보 수 카운트: 해당 partner와 ACTIVE 제휴가 없는 admin 수
    @Query(value = """
        SELECT COUNT(*)
        FROM admin a
        WHERE NOT EXISTS (
            SELECT 1 FROM paper pa
            WHERE pa.admin_id = a.id
              AND pa.partner_id = :partnerId
              AND pa.is_activated = 'ACTIVE'
        )
        """, nativeQuery = true)
    long countPartner(@Param("partnerId") Long partnerId);

    // 랜덤 오프셋으로 1~N건 가져오기 (LIMIT :offset, :limit)
    @Query(value = """
        SELECT a.*
        FROM admin a
        WHERE NOT EXISTS (
            SELECT 1 FROM paper pa
            WHERE pa.admin_id = a.id
              AND pa.partner_id = :partnerId
              AND pa.is_activated = 'ACTIVE'
        )
        LIMIT :offset, :limit
        """, nativeQuery = true)
    List<Admin> findPartnerWithOffset(@Param("partnerId") Long partnerId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    @Query("""
        SELECT DISTINCT a
        FROM Admin a
        LEFT JOIN FETCH a.member
        WHERE a.point IS NOT NULL
          AND function('ST_Contains', function('ST_GeomFromText', :wkt, 4326), a.point) = true
        """)
    List<Admin> findAllWithinViewportWithMember(@Param("wkt") String wkt, Pageable pageable);

    @Query("""
        SELECT DISTINCT a
        FROM Admin a
        LEFT JOIN FETCH a.member
        WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        """)
    List<Admin> searchAdminByKeywordWithMember(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
