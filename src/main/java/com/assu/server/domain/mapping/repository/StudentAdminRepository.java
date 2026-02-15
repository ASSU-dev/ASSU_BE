package com.assu.server.domain.mapping.repository;

import com.assu.server.domain.mapping.dto.StudentAdminResponseDTO;
import com.assu.server.domain.mapping.entity.StudentAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

public interface StudentAdminRepository extends JpaRepository<StudentAdmin, Long> {

    // 1. 단순 카운트 (기존 유지)
    @Query("select count(sa) from StudentAdmin sa where sa.admin.id = :adminId")
    Long countAllByAdminId(@Param("adminId") Long adminId);

    // 2. 이번 달 카운트 (기존 유지)
    @Query("select count(sa) from StudentAdmin sa where sa.admin.id = :adminId and sa.createdAt >= :from and sa.createdAt < :to")
    Long countByAdminIdBetween(@Param("adminId") Long adminId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    default Long countThisMonthByAdminId(Long adminId) {
        LocalDateTime from = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime to = LocalDateTime.now();
        return countByAdminIdBetween(adminId, from, to);
    }
     @Query("""
    SELECT COUNT(DISTINCT pu.student.id)
    FROM PartnershipUsage pu
    JOIN pu.paper pc
    JOIN pc.paper p
    WHERE p.admin.id = :adminId
    AND pu.createdAt >= :start
    AND pu.createdAt < :end
    """)
Long countTodayUsersByAdmin(
    @Param("adminId") Long adminId,
    @Param("start") LocalDateTime start,
    @Param("end") LocalDateTime end
);
    @Query("""
        SELECT new com.assu.server.domain.mapping.dto.StudentAdminResponseDTO.StoreUsageWithPaper(
          p.id, p.storeId, s.name, COUNT(pu.id)
        )
        FROM Paper p
        JOIN Store s ON s.id = p.storeId
        JOIN PaperContent pc ON pc.paper.id = p.id
        JOIN PartnershipUsage pu ON pu.paper.id = pc.id
        WHERE p.admin.id = :adminId
        GROUP BY p.id, p.storeId, s.name
        ORDER BY COUNT(pu.id) DESC, p.id ASC
        """)
    List<StudentAdminResponseDTO.StoreUsageWithPaper> findUsageByStoreWithPaper(@Param("adminId") Long adminId);
}