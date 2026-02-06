package com.assu.server.domain.admin.repository;

import java.util.List;
import java.util.Optional;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.user.entity.enums.Department;
import com.assu.server.domain.user.entity.enums.Major;
import com.assu.server.domain.user.entity.enums.University;

public interface AdminRepository extends JpaRepository<Admin, Long> {

	// 여기 예원이 머지하고 수정
	@Query("SELECT a FROM Admin a WHERE " +
		"(a.university = :university AND a.department IS NULL AND a.major IS NULL) OR " +
		"(a.university = :university AND a.department = :department AND a.major IS NULL) OR " +
		"(a.university = :university AND a.department = :department AND a.major = :major)")
	List<Admin> findMatchingAdmins(@Param("university") University university,
		@Param("department") Department department,
		@Param("major") Major major);

	Optional<Admin> findByName(String name);

    // 후보 수 카운트: 해당 partner와 ACTIVE 제휴가 없는 admin 수
    @Query(value = """
        SELECT COUNT(*)
        FROM admin a
        LEFT JOIN paper pa
          ON pa.admin_id = a.id
         AND pa.partner_id = :partnerId
         AND pa.is_activated = 'ACTIVE'
        WHERE pa.id IS NULL
        """, nativeQuery = true)
    long countPartner(@Param("partnerId") Long partnerId);

    // 랜덤 오프셋으로 1~N건 가져오기 (LIMIT :offset, :limit)
    @Query(value = """
        SELECT a.*
        FROM admin a
        LEFT JOIN paper pa
          ON pa.admin_id = a.id
         AND pa.partner_id = :partnerId
         AND pa.is_activated = 'ACTIVE'
        WHERE pa.id IS NULL
        LIMIT :offset, :limit
        """, nativeQuery = true)
    List<Admin> findPartnerWithOffset(@Param("partnerId") Long partnerId,
                                                         @Param("offset") int offset,
                                                         @Param("limit") int limit);

    @Query(value = """
        SELECT a.*
        FROM admin a
        WHERE a.point IS NOT NULL
          AND ST_Contains(ST_GeomFromText(:wkt, 4326), a.point)
        """, nativeQuery = true)
    List<Admin> findAllWithinViewport(@Param("wkt") String wkt);

    @Query("""
        SELECT DISTINCT a
        FROM Admin a
        LEFT JOIN FETCH a.member
        WHERE a.point IS NOT NULL
          AND function('ST_Contains', function('ST_GeomFromText', :wkt, 4326), a.point) = true
        """)
    List<Admin> findAllWithinViewportWithMember(@Param("wkt") String wkt);

    @Query("""
        select distinct a
        from Admin a
        where lower(a.name) like lower(concat('%', :keyword, '%'))
        """)
    List<Admin> searchAdminByKeyword(
            @Param("keyword") String keyword
    );

    @Query("""
        SELECT DISTINCT a
        FROM Admin a
        LEFT JOIN FETCH a.member
        WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        """)
    List<Admin> searchAdminByKeywordWithMember(
            @Param("keyword") String keyword
    );

    Long member(Member member);

	Optional<Admin> findById(Long id);
}
