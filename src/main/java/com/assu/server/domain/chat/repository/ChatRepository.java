package com.assu.server.domain.chat.repository;

import com.assu.server.domain.chat.dto.ChatRoomListResultDTO;
import com.assu.server.domain.chat.entity.ChattingRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRepository extends JpaRepository<ChattingRoom, Long> {
    @Query("""
    SELECT new com.assu.server.domain.chat.dto.ChatRoomListResultDTO (
    r.id,
    (SELECT m.message
     FROM Message m
     WHERE m.chattingRoom.id = r.id
     AND m.createdAt = (
         SELECT MAX(m2.createdAt)
         FROM Message m2
         WHERE m2.chattingRoom.id = r.id
     )
    ),
    (SELECT MAX(m.createdAt)
     FROM Message m
     WHERE m.chattingRoom.id = r.id
    ),
    (SELECT COUNT(m)
     FROM Message m
     WHERE m.chattingRoom.id = r.id
     AND m.receiver.id = :memberId
     AND m.isRead = false),
     CASE
         WHEN pm.id   IS NULL AND am.id = :memberId THEN -1
         WHEN am.id   IS NULL AND pm.id = :memberId THEN -1
         WHEN pm.id = :memberId THEN a.id
         ELSE p.id
       END,
     CASE
         WHEN pm.id   IS NULL AND am.id = :memberId THEN -1
         WHEN am.id   IS NULL AND pm.id = :memberId THEN -1
         WHEN pm.id = :memberId THEN a.name
         ELSE p.name
       END,
     CASE
         WHEN pm.id   IS NULL AND am.id = :memberId THEN -1
         WHEN am.id   IS NULL AND pm.id = :memberId THEN -1
         WHEN pm.id = :memberId THEN am.profileUrl
         ELSE pm.profileUrl
       END,
     CASE
          WHEN pm.id   IS NULL AND am.id = :memberId THEN '-1'
          WHEN am.id   IS NULL AND pm.id = :memberId THEN '-1'
          WHEN pm.id = :memberId THEN a.phoneNum
          ELSE p.phoneNum
        END
            )
            FROM ChattingRoom r
      LEFT JOIN r.partner p
      LEFT JOIN p.member  pm
      LEFT JOIN r.admin   a
      LEFT JOIN a.member  am
      WHERE pm.id = :memberId
      OR am.id = :memberId
      ORDER BY
             (SELECT MAX(m.createdAt)
              FROM Message m
              WHERE m.chattingRoom.id = r.id) DESC
    """)
    List<ChatRoomListResultDTO> findChattingRoomsByMemberId(@Param("memberId") Long memberId);

    @Query("""
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM ChattingRoom c
        WHERE c.admin.id = :adminId AND c.partner.id = :partnerId
    """)
    Boolean checkChattingRoomByAdminIdAndPartnerId(
            @Param("adminId") Long adminId,
            @Param("partnerId") Long partnerId
    );


    @Query("""
        SELECT c
        FROM ChattingRoom c
        WHERE c.admin.id = :adminId AND c.partner.id = :partnerId
    """)
    ChattingRoom findChattingRoomByAdminIdAndPartnerId(
            @Param("adminId") Long adminId,
            @Param("partnerId")Long partnerId
    );
}
