package com.assu.server.domain.partnership.repository;

import com.assu.server.domain.partnership.entity.Goods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

	List<Goods> findByContentId(Long contentId);

    @Modifying
    @Query("delete from Goods g where g.content.id in :contentIds")
    void deleteAllByContentIds(@Param("contentIds") List<Long> contentIds);
}
