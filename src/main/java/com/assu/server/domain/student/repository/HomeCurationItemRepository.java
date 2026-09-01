package com.assu.server.domain.student.repository;

import com.assu.server.domain.student.entity.HomeCurationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HomeCurationItemRepository extends JpaRepository<HomeCurationItem, Long> {

    @Query("SELECT i FROM HomeCurationItem i JOIN FETCH i.store s LEFT JOIN FETCH s.partner p LEFT JOIN FETCH p.member WHERE i.homeCuration.id = :curationId ORDER BY i.groupIndex ASC, i.sortOrder ASC")
    List<HomeCurationItem> findByHomeCurationIdWithStoreAndPartner(@Param("curationId") Long curationId);

    List<HomeCurationItem> findByHomeCurationIdOrderByGroupIndexAscSortOrderAsc(Long homeCurationId);
}
