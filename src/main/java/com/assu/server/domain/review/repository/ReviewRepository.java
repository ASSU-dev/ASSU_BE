package com.assu.server.domain.review.repository;

import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.common.entity.enums.ReportedStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByPartnerId(Long partnerId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Review r SET r.student = null WHERE r.student.id = :studentId")
    void anonymizeStudentByStudentId(@Param("studentId") Long studentId);
    @Query("""
                SELECT r
                FROM Review r
                WHERE r.student.id = :memberId
                AND r.status = :status
                AND r.student.status = :studentStatus
                ORDER BY r.createdAt DESC
            """)
    Page<Review> findByMemberIdAndStatusAndStudentStatus(
            @Param("memberId") Long memberId,
            @Param("status") ReportedStatus status,
            @Param("studentStatus") ReportedStatus studentStatus,
            Pageable pageable
    );

    @Query("""
            SELECT r
            FROM Review r
            WHERE r.student.id = :memberId
        """)
    Page<Review> findByMemberId(@Param("memberId") Long memberId, Pageable pageable);

    @Query("""
                SELECT r
                FROM Review r
                WHERE r.store.id = :storeId
                AND r.status = :status
                AND r.student.status = :studentStatus
                ORDER BY r.createdAt DESC
            """)
    Page<Review> findByStoreIdAndStatusAndStudentStatus(
            @Param("storeId") Long storeId,
            @Param("status") ReportedStatus status,
            @Param("studentStatus") ReportedStatus studentStatus,
            Pageable pageable
    );

    Page<Review> findByStoreIdOrderByCreatedAtDesc(Long id, Pageable pageable);// 최신순 정렬

    Page<Review> findByStoreId(Long id, Pageable pageable);

    @Query("SELECT AVG(r.rate) FROM Review r WHERE r.store.id = :storeId AND r.status = :status AND r.student.status = :studentStatus")
    Float standardScoreWithStatus(
            @Param("storeId") Long storeId,
            @Param("status") ReportedStatus status,
            @Param("studentStatus") ReportedStatus studentStatus
    );

    @Query("SELECT AVG(r.rate) FROM Review r WHERE r.store.id = :storeId")
    Float standardScore(Long storeId);

    Long store(Store store);
}
