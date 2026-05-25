package com.assu.server.domain.admin.repository;

import java.util.List;

import com.assu.server.domain.admin.entity.Admin;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    boolean existsByPhoneNum(String phoneNum);

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

	// 후보 수 카운트
	@Query("""
        SELECT COUNT(a)
        FROM Admin a
        WHERE NOT EXISTS (
            SELECT 1 FROM Paper pa
            WHERE pa.admin = a
              AND pa.partner.id = :partnerId
              AND pa.isActivated = com.assu.server.domain.common.enums.ActivationStatus.ACTIVE
        )
        """)
	long countPartner(@Param("partnerId") Long partnerId);

	// 랜덤 오프셋 조회 (네이티브 빼고 Pageable 적용)
	@Query("""
        SELECT a
        FROM Admin a
        WHERE NOT EXISTS (
            SELECT 1 FROM Paper pa
            WHERE pa.admin = a
              AND pa.partner.id = :partnerId
              AND pa.isActivated = com.assu.server.domain.common.enums.ActivationStatus.ACTIVE
        )
        """)
	List<Admin> findPartnerWithOffset(@Param("partnerId") Long partnerId, Pageable pageable);

    @Query("""
        SELECT DISTINCT a
        FROM Admin a
        LEFT JOIN FETCH a.member
        WHERE a.point IS NOT NULL
      AND ST_Contains(ST_GeomFromText(:wkt, 4326), a.point) = true
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
