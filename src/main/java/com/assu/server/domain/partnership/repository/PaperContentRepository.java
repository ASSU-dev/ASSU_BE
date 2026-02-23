package com.assu.server.domain.partnership.repository;

import com.assu.server.domain.partnership.entity.PaperContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaperContentRepository extends JpaRepository<PaperContent, Long> {

	List<PaperContent> findByPaperId(Long paperId);

    @Query("""
           select distinct pc
           from PaperContent pc
           left join fetch pc.goods g
           where pc.paper.id in :paperIds
           """)
    List<PaperContent> findAllByPaperIdInFetchGoods(@Param("paperIds") List<Long> paperIds);

    @Query("""
           select distinct pc
           from PaperContent pc
           left join fetch pc.goods g
           where pc.paper.id in :paperIds
           """)
    List<PaperContent> findAllByOnePaperIdInFetchGoods(@Param("paperIds") Long paperIds);

    Optional<PaperContent> findById(Long id);

    Optional<PaperContent> findTopByPaperIdOrderByIdDesc(Long paperId);

    @Query("""
        SELECT pc
        FROM PaperContent pc
        WHERE pc.paper.store.id IN :storeIds
        ORDER BY pc.id DESC
        """)
    List<PaperContent> findTopByStoreIdIn(@Param("storeIds") List<Long> storeIds);

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

}
