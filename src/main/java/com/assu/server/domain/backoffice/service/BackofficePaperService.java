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

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.domain.backoffice.dto.BackofficePaperCreateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficePaperContentCreateRequestDTO;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class
BackofficePaperService {

    private final PaperRepository paperRepository;
    private final PaperContentRepository paperContentRepository;
    private final AdminRepository adminRepository;
    private final StoreRepository storeRepository;

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
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        if(paper.getIsActivated() == ActivationStatus.ACTIVE){
            throw new DatabaseException(ErrorStatus.ALREADY_APPROVED);
        }
        paper.changeStatus(ActivationStatus.ACTIVE);
        paperRepository.save(paper);
    }

    public void rejectPaper(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        paper.changeStatus(ActivationStatus.INACTIVE);
        paperRepository.save(paper);
    }

    public void expirePaper(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        paper.changeStatus(ActivationStatus.INACTIVE);
        paperRepository.save(paper);
    }

    public WritePartnershipResponseDTO createPaper(BackofficePaperCreateRequestDTO req) {
        Admin admin = adminRepository.findById(req.adminId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));
        Store store = storeRepository.findById(req.storeId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER)); // 가게 없을 시 예외

        Paper paper = Paper.builder()
                .admin(admin)
                .store(store)
                .partner(null)
                .isActivated(ActivationStatus.ACTIVE) // 생성 시 ACTIVE 활성화 상태로 설정
                .partnershipPeriodStart(req.partnershipPeriodStart())
                .partnershipPeriodEnd(req.partnershipPeriodEnd())
                .build();
        paper = paperRepository.save(paper);

        return WritePartnershipResponseDTO.of(paper, List.of());
    }

    public WritePartnershipResponseDTO addPaperContents(Long paperId, BackofficePaperContentCreateRequestDTO req) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        if (req.options() != null && !req.options().isEmpty()) {
            paperContentRepository.saveAll(req.options().stream().map(o -> o.toPaperContent(paper)).toList());
        }

        return WritePartnershipResponseDTO.of(paper, paperContentRepository.findByPaperIdOrderByIdAsc(paper.getId()));
    }

    private List<WritePartnershipResponseDTO> buildPartnershipDTOs(List<Paper> papers) {
        if (papers == null || papers.isEmpty()) return List.of();

        List<Long> paperIds = papers.stream().map(Paper::getId).toList();
        List<PaperContent> allContents = paperContentRepository.findAllByPaperIdIn(paperIds, Pageable.unpaged()).getContent();

        Map<Long, List<PaperContent>> byPaperId = allContents.stream()
                .collect(Collectors.groupingBy(pc -> pc.getPaper().getId()));

        List<WritePartnershipResponseDTO> result = new ArrayList<>(papers.size());
        for (Paper p : papers) {
            result.add(WritePartnershipResponseDTO.of(p, byPaperId.getOrDefault(p.getId(), List.of())));
        }
        return result;
    }
}
