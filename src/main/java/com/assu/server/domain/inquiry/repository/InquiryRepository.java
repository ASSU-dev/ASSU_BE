package com.assu.server.domain.inquiry.repository;

import com.assu.server.domain.inquiry.entity.Inquiry;
import com.assu.server.domain.inquiry.entity.Inquiry.Status;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    Page<Inquiry> findByMemberId(Long memberId, Pageable pageable);
    Page<Inquiry> findByMemberIdAndStatus(Long memberId, Status status, Pageable pageable);
    Page<Inquiry> findByStatus(Status status, Pageable pageable);

    @Query("SELECT i FROM Inquiry i WHERE i.status = :status AND (i.title LIKE %:keyword% OR i.content LIKE %:keyword%)")
    Page<Inquiry> findByStatusAndKeyword(@Param("status") Status status, @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT i FROM Inquiry i WHERE i.title LIKE %:keyword% OR i.content LIKE %:keyword%")
    Page<Inquiry> findByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
