package com.assu.server.domain.chat.repository;


import com.assu.server.domain.chat.entity.Block;
import com.assu.server.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {
    boolean existsByBlockerAndBlocked(Member blocker, Member blocked);

    void deleteByBlockerAndBlocked(Member blocker, Member blocked);

    List<Block> findByBlocker(Member blocker);

    // BlockRepository.java
    @Query("SELECT COUNT(b) > 0 FROM Block b " +
            "WHERE (b.blocker = :user1 AND b.blocked = :user2) " +
            "OR (b.blocker = :user2 AND b.blocked = :user1)" +
            "ORDER BY b.createdAt DESC")
    boolean existsBlockRelationBetween(@Param("user1") Member user1, @Param("user2") Member user2);

    @Modifying
    @Query("DELETE FROM Block b WHERE (b.blocker.id = :id1 AND b.blocked.id = :id2) OR (b.blocker.id = :id2 AND b.blocked.id = :id1)")
    void deleteAllBlocksBetween(@Param("id1") Long id1, @Param("id2") Long id2);

}
