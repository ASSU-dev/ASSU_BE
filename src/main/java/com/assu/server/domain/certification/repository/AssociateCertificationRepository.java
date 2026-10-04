package com.assu.server.domain.certification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.assu.server.domain.certification.entity.AssociateCertification;

public interface AssociateCertificationRepository extends JpaRepository<AssociateCertification, Long> {

    @Modifying(flushAutomatically = true)
    @Query("UPDATE AssociateCertification a SET a.student = null WHERE a.student.id = :studentId")
    void anonymizeStudentByStudentId(@Param("studentId") Long studentId);
}
