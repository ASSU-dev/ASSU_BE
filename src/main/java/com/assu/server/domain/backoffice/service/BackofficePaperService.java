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

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BackofficePaperService {

    private final PaperRepository paperRepository;
    private final PaperContentRepository paperContentRepository;

    @Transactional(readOnly = true)
    public Page<WritePartnershipResponseDTO> getPapers(Pageable pageable) {
        Page<Paper> paperPage = paperRepository.findAll(pageable);
        List<Paper> papers = paperPage.getContent().stream()
                .filter(p -> p.getAdmin() != null && p.getStore() != null)
                .toList();
        List<WritePartnershipResponseDTO> dtos = buildPartnershipDTOs(papers);
        return new PageImpl<>(dtos, pageable, paperPage.getTotalElements());
    }

    public void approvePaper(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER)); // 혹은 NO_SUCH_PAPER 대체

        paper.setIsActivated(ActivationStatus.ACTIVE);
        paperRepository.save(paper);
    }

    public void rejectPaper(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));

        paper.setIsActivated(ActivationStatus.INACTIVE);
        paperRepository.save(paper);
    }

    public void expirePaper(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));

        paper.setIsActivated(ActivationStatus.INACTIVE);
        paperRepository.save(paper);
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
