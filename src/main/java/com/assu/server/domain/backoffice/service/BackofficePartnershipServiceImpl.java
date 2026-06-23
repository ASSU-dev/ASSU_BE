package com.assu.server.domain.backoffice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackofficePartnershipServiceImpl implements BackofficePartnershipService {

    private final PaperRepository paperRepository;
    private final PaperContentRepository paperContentRepository;

    @Override
    public Page<WritePartnershipResponseDTO> getPartnershipsByAdmin(Long adminId, Pageable pageable) {
        Page<Paper> paperPage = paperRepository.findByAdmin_IdAndIsActivated(adminId, ActivationStatus.ACTIVE, pageable);
        List<Paper> papers = paperPage.getContent().stream()
                .filter(p -> p.getStore() != null)
                .toList();
        List<WritePartnershipResponseDTO> dtos = buildPartnershipDTOs(papers);
        return new PageImpl<>(dtos, pageable, paperPage.getTotalElements());
    }

    @Override
    public Page<WritePartnershipResponseDTO> getPartnershipsByStore(Long storeId, Pageable pageable) {
        Page<Paper> paperPage = paperRepository.findByStore_IdAndIsActivated(storeId, ActivationStatus.ACTIVE, pageable);
        List<Paper> papers = paperPage.getContent().stream()
                .filter(p -> p.getAdmin() != null)
                .toList();
        List<WritePartnershipResponseDTO> dtos = buildPartnershipDTOs(papers);
        return new PageImpl<>(dtos, pageable, paperPage.getTotalElements());

    }

    @Override
    public Page<WritePartnershipResponseDTO> getPartnerships(Pageable pageable) {
        Page<Paper> paperPage = paperRepository.findAll(pageable);
        List<Paper> papers = paperPage.getContent().stream()
                .filter(p -> p.getAdmin() != null && p.getStore() != null)
                .toList();
        List<WritePartnershipResponseDTO> dtos = buildPartnershipDTOs(papers);
        return new PageImpl<>(dtos, pageable, paperPage.getTotalElements());
    }


    private List<WritePartnershipResponseDTO> buildPartnershipDTOs(List<Paper> papers) {
        if (papers == null || papers.isEmpty()) return List.of();

        List<Long> paperIds = papers.stream().map(Paper::getId).toList();
        List<PaperContent> allContents = paperContentRepository.findAllByPaperIdIn(paperIds, Pageable.unpaged()).getContent();

        Map<Long, List<PaperContent>> byPaperId = allContents.stream()
                .collect(Collectors.groupingBy(pc -> pc.getPaper().getId()));

        List<WritePartnershipResponseDTO> result = new ArrayList<>(papers.size());
        for (Paper p : papers) {
            List<PaperContent> contents = byPaperId.getOrDefault(p.getId(), List.of());
            List<List<Goods>> goodsBatches = contents.stream()
                    .map(pc -> pc.getGoods() == null ? List.<Goods>of() : pc.getGoods())
                    .toList();
            result.add(WritePartnershipResponseDTO.of(p, contents, goodsBatches));
        }
        return result;
    }
}
