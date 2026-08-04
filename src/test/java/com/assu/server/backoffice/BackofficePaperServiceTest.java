package com.assu.server.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.backoffice.dto.BackofficePaperContentCreateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficePaperCreateRequestDTO;
import com.assu.server.domain.backoffice.service.BackofficePaperService;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.dto.PartnershipGoodsRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipOptionRequestDTO;
import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import com.assu.server.domain.partnership.repository.GoodsRepository;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;

@ExtendWith(MockitoExtension.class)
class BackofficePaperServiceTest {

    @InjectMocks
    private BackofficePaperService backofficePaperService;

    @Mock
    private PaperRepository paperRepository;

    @Mock
    private PaperContentRepository paperContentRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private GoodsRepository goodsRepository;

    @Test
    @DisplayName("등록된 모든 제휴 계약서 목록을 페이징하여 조회한다")
    void getPapers_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        Admin admin = Admin.builder().id(1L).name("숭실학생회").build();
        Store store = Store.builder().id(100L).name("역전할머니").build();

        Paper paper = Paper.builder()
                .id(10L)
                .admin(admin)
                .store(store)
                .partnershipPeriodStart(LocalDate.now())
                .partnershipPeriodEnd(LocalDate.now().plusDays(10))
                .isActivated(ActivationStatus.SUSPEND)
                .build();

        Page<Paper> paperPage = new PageImpl<>(List.of(paper), pageable, 1);
        when(paperRepository.findAll(any(Pageable.class))).thenReturn(paperPage);

        Page<PaperContent> paperContentPage = new PageImpl<>(List.of());
        when(paperContentRepository.findAllByPaperIdIn(any(), any())).thenReturn(paperContentPage);

        // when
        Page<WritePartnershipResponseDTO> response = backofficePaperService.getPapers(pageable);

        // then
        assertThat(response.getContent()).hasSize(1);
        var dto = response.getContent().get(0);
        assertThat(dto.partnershipId()).isEqualTo(10L);
        assertThat(dto.isActivated()).isEqualTo(ActivationStatus.SUSPEND);
    }

    @Test
    @DisplayName("제휴 계약서를 승인하여 ACTIVE 상태로 바꾼다")
    void approvePaper_Success() {
        // given
        Long paperId = 10L;
        Paper paper = Paper.builder()
                .id(paperId)
                .isActivated(ActivationStatus.SUSPEND)
                .build();

        when(paperRepository.findById(paperId)).thenReturn(Optional.of(paper));

        // when
        backofficePaperService.approvePaper(paperId);

        // then
        assertThat(paper.getIsActivated()).isEqualTo(ActivationStatus.ACTIVE);
        verify(paperRepository).save(paper);
    }

    @Test
    @DisplayName("제휴 계약서를 거부하여 INACTIVE 상태로 바꾼다")
    void rejectPaper_Success() {
        // given
        Long paperId = 10L;
        Paper paper = Paper.builder()
                .id(paperId)
                .isActivated(ActivationStatus.SUSPEND)
                .build();

        when(paperRepository.findById(paperId)).thenReturn(Optional.of(paper));

        // when
        backofficePaperService.rejectPaper(paperId);

        // then
        assertThat(paper.getIsActivated()).isEqualTo(ActivationStatus.INACTIVE);
        verify(paperRepository).save(paper);
    }

    @Test
    @DisplayName("제휴 계약서를 만료 처리하여 INACTIVE 상태로 바꾼다")
    void expirePaper_Success() {
        // given
        Long paperId = 10L;
        Paper paper = Paper.builder()
                .id(paperId)
                .isActivated(ActivationStatus.ACTIVE)
                .build();

        when(paperRepository.findById(paperId)).thenReturn(Optional.of(paper));

        // when
        backofficePaperService.expirePaper(paperId);

        // then
        assertThat(paper.getIsActivated()).isEqualTo(ActivationStatus.INACTIVE);
        verify(paperRepository).save(paper);
    }

    @Test
    @DisplayName("임의의 제휴 계약서를 성공적으로 생성한다")
    void createPaper_Success() {
        // given
        Long adminId = 1L;
        Long storeId = 100L;
        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now().plusDays(30);
        BackofficePaperCreateRequestDTO req = new BackofficePaperCreateRequestDTO(adminId, storeId, start, end);

        Admin admin = Admin.builder().id(adminId).name("숭실학생회").build();
        Store store = Store.builder().id(storeId).name("역전할머니").build();
        Paper paper = Paper.builder()
                .id(10L)
                .admin(admin)
                .store(store)
                .partnershipPeriodStart(start)
                .partnershipPeriodEnd(end)
                .isActivated(ActivationStatus.ACTIVE)
                .build();

        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(paperRepository.save(any(Paper.class))).thenReturn(paper);

        // when
        WritePartnershipResponseDTO response = backofficePaperService.createPaper(req);

        // then
        assertThat(response.partnershipId()).isEqualTo(10L);
        assertThat(response.isActivated()).isEqualTo(ActivationStatus.ACTIVE);
    }

    @Test
    @DisplayName("제휴 계약서에 제휴 내용(옵션)을 성공적으로 추가한다")
    void addPaperContents_Success() {
        // given
        Long paperId = 10L;
        Admin admin = Admin.builder().id(1L).name("숭실학생회").build();
        Store store = Store.builder().id(100L).name("역전할머니").build();
        Paper paper = Paper.builder()
                .id(paperId)
                .admin(admin)
                .store(store)
                .partnershipPeriodStart(LocalDate.now())
                .partnershipPeriodEnd(LocalDate.now().plusDays(30))
                .isActivated(ActivationStatus.ACTIVE)
                .build();

        PartnershipGoodsRequestDTO goodsReq = new PartnershipGoodsRequestDTO("제휴혜택상품");
        PartnershipOptionRequestDTO optReq = new PartnershipOptionRequestDTO(
                OptionType.SERVICE,
                CriterionType.PRICE,
                false,
                null,
                10000L,
                "카테고리명",
                null,
                "혜택설명",
                List.of(goodsReq)
        );
        BackofficePaperContentCreateRequestDTO req = new BackofficePaperContentCreateRequestDTO(List.of(optReq));

        PaperContent paperContent = optReq.toPaperContent(paper);
        List<PaperContent> savedContents = List.of(paperContent);

        when(paperRepository.findById(paperId)).thenReturn(Optional.of(paper));
        when(paperContentRepository.saveAll(any())).thenReturn(savedContents);
        when(paperContentRepository.findAllByOnePaperIdInFetchGoods(paperId)).thenReturn(savedContents);

        // when
        WritePartnershipResponseDTO response = backofficePaperService.addPaperContents(paperId, req);

        // then
        assertThat(response.partnershipId()).isEqualTo(paperId);
        verify(paperContentRepository).saveAll(any());
        verify(goodsRepository).saveAll(any());
    }
}

