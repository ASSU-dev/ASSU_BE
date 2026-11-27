package com.assu.server.domain.certification.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.assu.server.domain.certification.entity.AssociateCertification;

public interface AssociateCertificationRepository extends JpaRepository<AssociateCertification, Long> {

    @Modifying(flushAutomatically = true)
    @Query("UPDATE AssociateCertification a SET a.student = null WHERE a.student.id = :studentId")
    void anonymizeStudentByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT a FROM AssociateCertification a " +
        "WHERE a.student.id = :studentId AND a.store.id = :storeId " +
        "AND a.isCertified = true AND a.createdAt >= :since " +
        "ORDER BY a.createdAt DESC, a.id DESC")
    List<AssociateCertification> findRecentByStudentIdAndStoreId(
        @Param("studentId") Long studentId,
        @Param("storeId") Long storeId,
        @Param("since") LocalDateTime since
    );

    @Query("SELECT a FROM AssociateCertification a JOIN FETCH a.student " +
        "WHERE a.sessionId = :sessionId AND a.store.id = :storeId " +
        "AND a.isCertified = true AND a.createdAt >= :since")
    List<AssociateCertification> findRecentBySessionIdAndStoreId(
        @Param("sessionId") Long sessionId,
        @Param("storeId") Long storeId,
        @Param("since") LocalDateTime since
    );

    @Modifying(flushAutomatically = true)
    @Query("UPDATE AssociateCertification a SET a.usedAt = :usedAt WHERE a.id = :id AND a.usedAt IS NULL")
    int markUsedIfUnused(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);
}
