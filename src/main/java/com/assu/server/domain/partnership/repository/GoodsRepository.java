package com.assu.server.domain.partnership.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.assu.server.domain.partnership.entity.Goods;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

	List<Goods> findByContentId(Long contentId);

    @Query("SELECT g FROM Goods g WHERE g.content.id IN :contentIds")
    List<Goods> findByContentIdIn(@Param("contentIds") List<Long> contentIds);

    @Modifying
    @Query("delete from Goods g where g.content.id in :contentIds")
    void deleteAllByContentIds(@Param("contentIds") List<Long> contentIds);
}
