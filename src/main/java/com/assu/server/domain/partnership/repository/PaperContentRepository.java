package com.assu.server.domain.partnership.repository;

import com.assu.server.domain.partnership.entity.PaperContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaperContentRepository extends JpaRepository<PaperContent, Long> {

	List<PaperContent> findByPaperIdOrderByIdAsc(Long paperId);

    Optional<PaperContent> findById(Long id);

    Optional<PaperContent> findTopByPaperIdOrderByIdDesc(Long paperId);

    @Query("""
        SELECT pc
        FROM PaperContent pc
        JOIN FETCH pc.paper p
        JOIN FETCH p.store s
        WHERE s.id IN :storeIds
        ORDER BY pc.id DESC
        """)
    List<PaperContent> findTopByStoreIdIn(@Param("storeIds") List<Long> storeIds);

    @Query(value = """
           select pc
           from PaperContent pc
           join pc.paper p
           where p.id in :paperIds
           order by pc.id
           """,
           countQuery = """
           select count(pc)
           from PaperContent pc
           join pc.paper p
           where p.id in :paperIds
           """)
    Page<PaperContent> findAllByPaperIdIn(@Param("paperIds") List<Long> paperIds, Pageable pageable);

    @Query(value = """
        WITH ranked AS (
          SELECT pc.*,
                 ROW_NUMBER() OVER (PARTITION BY pc.paper_id ORDER BY pc.id DESC) AS rn
          FROM paper_content pc
          WHERE pc.paper_id IN :paperIds
        )
        SELECT * FROM ranked WHERE rn = 1
        """, nativeQuery = true)
    List<PaperContent> findLatestByPaperIds(@Param("paperIds") List<Long> paperIds);

    List<PaperContent> findByPaperIdInOrderByIdAsc(List<Long> paperIds);

}
