package com.assu.server.domain.student.repository;

import com.assu.server.domain.student.entity.StampEventApplicant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StampEventApplicantRepository extends JpaRepository<StampEventApplicant, Long> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM StampEventApplicant s WHERE s.student.id = :studentId")
    void deleteAllByStudentId(@Param("studentId") Long studentId);
}