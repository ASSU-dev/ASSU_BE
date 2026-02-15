package com.assu.server.domain.appreview.repository;

import com.assu.server.domain.appreview.entity.AppReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppReviewRepository extends JpaRepository<AppReview, Long> {
}