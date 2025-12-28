package com.assu.server.domain.partnership.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.assu.server.domain.partnership.entity.PaperContent;

public interface PaperContentRepository extends JpaRepository<PaperContent, Long> {

    Optional<PaperContent> findTopByPaperStoreIdOrderByIdDesc(Long storeId);

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

    @Query(value = """
SELECT pc.*
FROM paper_content pc
JOIN paper p ON p.id = pc.paper_id
WHERE p.store_id = :storeId
  AND p.is_activated = :active
  AND CURRENT_DATE BETWEEN p.partnership_period_start AND p.partnership_period_end
  AND (
       (pc.option_type = :service AND
         ((pc.criterion_type = :price AND pc.cost IS NOT NULL)
       OR  (pc.criterion_type = :headcount AND pc.cost IS NOT NULL AND pc.people IS NOT NULL)))
    OR (pc.option_type = :discount AND pc.discount IS NOT NULL)
  )
ORDER BY
  CASE pc.option_type
    WHEN :service THEN 0 ELSE 1 END,              -- SERVICE 우선
  CASE pc.criterion_type
    WHEN :price THEN 0
    WHEN :headcount THEN 1
    ELSE 2 END,                                   -- PRICE > HEADCOUNT > 기타
  pc.updated_at DESC,
  pc.id DESC
LIMIT 1
""", nativeQuery = true)
    Optional<PaperContent> findLatestValidByStoreIdNative(
            @Param("storeId") Long storeId,
            @Param("active") String active,            // ActivationStatus.ACTIVE.name()
            @Param("service") String service,          // OptionType.SERVICE.name()
            @Param("discount") String discount,        // OptionType.DISCOUNT.name()
            @Param("price") String price,              // CriterionType.PRICE.name()
            @Param("headcount") String headcount       // CriterionType.HEADCOUNT.name()
    );

    Optional<PaperContent> findTopByPaperIdOrderByIdDesc(Long paperId);

}
