package com.assu.server.domain.appreview.repository;

import com.assu.server.domain.appreview.entity.AppReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppReviewRepository extends JpaRepository<AppReview, Long> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM AppReview a WHERE a.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}