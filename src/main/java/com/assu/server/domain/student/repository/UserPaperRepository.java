package com.assu.server.domain.student.repository;

import com.assu.server.domain.store.entity.enums.StoreCategory;
import com.assu.server.domain.student.entity.UserPaper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserPaperRepository extends JpaRepository<UserPaper, Long> {

    @Query("""
        SELECT up FROM UserPaper up
        JOIN FETCH up.paper p
        LEFT JOIN FETCH p.store s
        LEFT JOIN FETCH p.admin a
        LEFT JOIN FETCH p.partner pt
        LEFT JOIN FETCH pt.member pm
        WHERE up.student.id = :studentId
          AND p.isActivated = com.assu.server.domain.common.enums.ActivationStatus.ACTIVE
          AND (:storeCategory IS NULL OR s.storeCategory = :storeCategory)
          AND (:adminId IS NULL OR a.id = :adminId)
        ORDER BY p.id DESC
    """)
    List<UserPaper> findActivePartnershipsByStudentId(
            @Param("studentId") Long studentId,
            @Param("storeCategory") StoreCategory storeCategory,
            @Param("adminId") Long adminId
    );

    @Query("""
        SELECT up FROM UserPaper up
        JOIN FETCH up.paper p
        JOIN FETCH up.paperContent pc
        WHERE up.student.id = :studentId
    """)
    List<UserPaper> findByStudentId(@Param("studentId") Long studentId);

}
