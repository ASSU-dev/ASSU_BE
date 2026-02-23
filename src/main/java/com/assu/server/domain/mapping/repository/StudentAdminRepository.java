package com.assu.server.domain.mapping.repository;

import com.assu.server.domain.mapping.dto.StoreUsageWithPaper;
import com.assu.server.domain.mapping.dto.StudentAdminResponseDTO;
import com.assu.server.domain.mapping.entity.StudentAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StudentAdminRepository extends JpaRepository<StudentAdmin, Long> {

    @Query("select count(sa) from StudentAdmin sa where sa.admin.id = :adminId")
    Long countAllByAdminId(@Param("adminId") Long adminId);

    @Query("select count(sa) from StudentAdmin sa where sa.admin.id = :adminId and sa.createdAt >= :from and sa.createdAt < :to")
    Long countByAdminIdBetween(@Param("adminId") Long adminId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""

            SELECT COUNT(DISTINCT pu.student.id)
        FROM PartnershipUsage pu, Paper p
        WHERE pu.paperId = p.id
          AND p.admin.id = :adminId
          AND pu.createdAt >= :start
          AND pu.createdAt < :end
        """)
    Long countTodayUsersByAdmin(
            @Param("adminId") Long adminId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
    SELECT new com.assu.server.domain.mapping.dto.StoreUsageWithPaper(
      p.id, p.store.id, p.store.name, COUNT(pu.id)
    )
    FROM PartnershipUsage pu
    JOIN Paper p ON pu.paperId = p.id
    WHERE p.admin.id = :adminId
    GROUP BY p.id, p.store.id, p.store.name
    ORDER BY COUNT(pu.id) DESC, p.id ASC
""")
    List<StoreUsageWithPaper> findUsageByStoreWithPaper(@Param("adminId") Long adminId);
    }