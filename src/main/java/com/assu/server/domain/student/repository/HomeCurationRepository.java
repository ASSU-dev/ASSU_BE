package com.assu.server.domain.student.repository;

import com.assu.server.domain.student.entity.HomeCuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface HomeCurationRepository extends JpaRepository<HomeCuration, Long> {

    @Query("SELECT hc FROM HomeCuration hc LEFT JOIN FETCH hc.featuredStore ORDER BY hc.id DESC LIMIT 1")
    Optional<HomeCuration> findLatest();

    Optional<HomeCuration> findTopByOrderByIdDesc();
}
