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
import com.assu.server.domain.backoffice.service.BackofficePaperService;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;

@ExtendWith(MockitoExtension.class)
class BackofficePaperServiceTest {

    @InjectMocks
    private BackofficePaperService backofficePaperService;

    @Mock
    private PaperRepository paperRepository;

    @Mock
    private PaperContentRepository paperContentRepository;

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
}
