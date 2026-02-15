package com.assu.server.domain.deviceToken.repository;

import com.assu.server.domain.deviceToken.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findAllByMemberIdAndActiveTrue(Long memberId);

    List<DeviceToken> findAllByTokenIn(List<String> tokens);

    Optional<DeviceToken> findByMemberIdAndToken(Long memberId, String token);

}
