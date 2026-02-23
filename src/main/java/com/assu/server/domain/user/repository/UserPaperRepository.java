package com.assu.server.domain.user.repository;

import com.assu.server.domain.user.entity.UserPaper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UserPaperRepository extends JpaRepository<UserPaper, Long> {

    @Query("""
        SELECT up FROM UserPaper up
        JOIN FETCH up.paper p
        LEFT JOIN FETCH p.store s
        LEFT JOIN FETCH p.admin a
        WHERE up.student.id = :studentId
          AND p.isActivated = com.assu.server.domain.common.enums.ActivationStatus.ACTIVE
        ORDER BY p.id DESC
    """)
    List<UserPaper> findActivePartnershipsByStudentId(@Param("studentId") Long studentId,
                                                      @Param("today") LocalDate today);

    boolean existsByStudentIdAndPaperId(Long studentId, Long paperId);

}
