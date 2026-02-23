package com.assu.server.domain.user.repository;

import com.assu.server.domain.user.entity.StampEventApplicant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StampEventApplicantRepository extends JpaRepository<StampEventApplicant, Long> {
}