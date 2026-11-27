package com.assu.server.domain.student.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.assu.server.domain.student.entity.PartnershipUsage;

public interface PartnershipUsageRepository extends JpaRepository<PartnershipUsage, Long> {

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PartnershipUsage p SET p.student = null WHERE p.student.id = :studentId")
    void anonymizeStudentByStudentId(@Param("studentId") Long studentId);

	@Query("SELECT pu FROM PartnershipUsage pu " +
		"WHERE pu.student.id = :studentId " +
		"AND pu.date >= :from " +
		"AND pu.date < :to " +
		"ORDER BY pu.date DESC")
	List<PartnershipUsage> findByStudentIdAndDateRange(
		@Param("studentId") Long studentId,
		@Param("from") LocalDate from,
		@Param("to") LocalDate to
	);

	Optional<PartnershipUsage> findById(Long id);


	@Query("SELECT pu FROM PartnershipUsage pu " +
		"WHERE pu.student.id = :studentId " +
		"AND (pu.isReviewed = false)")
	Page<PartnershipUsage> findByUnreviewedUsage(Long studentId, Pageable pageable);

	@Modifying(flushAutomatically = true)
	@Query("UPDATE PartnershipUsage pu SET pu.isReviewed = true " +
		"WHERE pu.id = :id AND pu.student.id = :studentId AND pu.isReviewed = false " +
		"AND EXISTS (SELECT 1 FROM Paper p WHERE p.id = pu.paperId AND p.store.id = :storeId)")
	int markReviewedIfUnreviewed(@Param("id") Long id, @Param("studentId") Long studentId,
		@Param("storeId") Long storeId);

	@Query("SELECT COUNT(pu) > 0 FROM PartnershipUsage pu, Paper p " +
		"WHERE pu.id = :id AND p.id = pu.paperId AND p.store.id = :storeId")
	boolean existsByIdAndStoreId(@Param("id") Long id, @Param("storeId") Long storeId);
}
