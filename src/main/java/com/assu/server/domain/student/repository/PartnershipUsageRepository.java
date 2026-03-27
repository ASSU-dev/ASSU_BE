package com.assu.server.domain.student.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.assu.server.domain.student.entity.PartnershipUsage;

public interface PartnershipUsageRepository extends JpaRepository<PartnershipUsage, Long> {

	@Query(value = """
       SELECT place
       FROM partnership_usage
       WHERE date = DATE(DATE_ADD(NOW(), INTERVAL 9 HOUR))
       GROUP BY place
       ORDER BY COUNT(*) DESC
       LIMIT 10
       """, nativeQuery = true)
	List<String> findTodayPopularPartnership();

	@Query("SELECT pu FROM PartnershipUsage pu " +
		"WHERE pu.student.id= :studentId " +
		"AND YEAR(pu.date) = :year " +
		"AND MONTH(pu.date) = :month " +
		"ORDER BY pu.date DESC")
	List<PartnershipUsage> findByYearAndMonth(
		@Param("studentId") Long studentId,
		@Param("year") int year,
		@Param("month") int month
	);

	Optional<PartnershipUsage> findById(Long id);


	@Query("SELECT pu FROM PartnershipUsage pu " +
		"WHERE pu.student.id = :studentId " +
		"AND (pu.isReviewed = false)")
	Page<PartnershipUsage> findByUnreviewedUsage(Long studentId, Pageable pageable);
}
