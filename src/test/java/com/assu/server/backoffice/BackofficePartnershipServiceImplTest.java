package com.assu.server.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

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
import com.assu.server.domain.backoffice.service.BackofficePartnershipServiceImpl;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;

@ExtendWith(MockitoExtension.class)
class BackofficePartnershipServiceImplTest {

    @InjectMocks
    private BackofficePartnershipServiceImpl backofficePartnershipService;

    @Mock
    private PaperRepository paperRepository;

    @Mock
    private PaperContentRepository paperContentRepository;

    @Test
    @DisplayName("백오피스 전용: 특정 학생회 ID 기준 활성 제휴 목록을 정상 조회한다")
    void getPartnershipsByAdmin_Success() {
        // given
        Long adminId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        Admin admin = Admin.builder().id(adminId).name("숭실학생회").build();
        Store store = Store.builder().id(100L).name("역전할머니").build();

        Paper paper = Paper.builder()
                .id(10L)
                .admin(admin)
                .store(store)
                .partnershipPeriodStart(LocalDate.now())
                .partnershipPeriodEnd(LocalDate.now().plusDays(10))
                .isActivated(ActivationStatus.ACTIVE)
                .build();

        Page<Paper> paperPage = new PageImpl<>(List.of(paper), pageable, 1);
        when(paperRepository.findByAdmin_IdAndIsActivated(eq(adminId), eq(ActivationStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(paperPage);

        Page<PaperContent> paperContentPage = new PageImpl<>(List.of());
        when(paperContentRepository.findAllByPaperIdIn(any(), any())).thenReturn(paperContentPage);

        // when
        Page<WritePartnershipResponseDTO> response = backofficePartnershipService.getPartnershipsByAdmin(adminId, pageable);

        // then
        assertThat(response.getContent()).hasSize(1);
        var dto = response.getContent().get(0);
        assertThat(dto.partnershipId()).isEqualTo(10L);
        assertThat(dto.storeName()).isEqualTo("역전할머니");
        assertThat(dto.adminName()).isEqualTo("숭실학생회");
    }

    @Test
    @DisplayName("백오피스 전용: 특정 가게 ID 기준 활성 제휴 목록을 정상 조회한다")
    void getPartnershipsByStore_Success() {
        // given
        Long storeId = 100L;
        Pageable pageable = PageRequest.of(0, 10);

        Admin admin = Admin.builder().id(1L).name("숭실학생회").build();
        Store store = Store.builder().id(storeId).name("역전할머니").build();

        Paper paper = Paper.builder()
                .id(10L)
                .admin(admin)
                .store(store)
                .partnershipPeriodStart(LocalDate.now())
                .partnershipPeriodEnd(LocalDate.now().plusDays(10))
                .isActivated(ActivationStatus.ACTIVE)
                .build();

        Page<Paper> paperPage = new PageImpl<>(List.of(paper), pageable, 1);
        when(paperRepository.findByStore_IdAndIsActivated(eq(storeId), eq(ActivationStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(paperPage);

        Page<PaperContent> paperContentPage = new PageImpl<>(List.of());
        when(paperContentRepository.findAllByPaperIdIn(any(), any())).thenReturn(paperContentPage);

        // when
        Page<WritePartnershipResponseDTO> response = backofficePartnershipService.getPartnershipsByStore(storeId, pageable);

        // then
        assertThat(response.getContent()).hasSize(1);
        var dto = response.getContent().get(0);
        assertThat(dto.partnershipId()).isEqualTo(10L);
        assertThat(dto.storeName()).isEqualTo("역전할머니");
        assertThat(dto.adminName()).isEqualTo("숭실학생회");
    }

    @Test
    @DisplayName("백오피스 전용: 등록된 모든 제휴 목록을 페이징하여 조회한다")
    void getPartnerships_Success() {
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
                .isActivated(ActivationStatus.ACTIVE)
                .build();

        Page<Paper> paperPage = new PageImpl<>(List.of(paper), pageable, 1);
        when(paperRepository.findAll(any(Pageable.class))).thenReturn(paperPage);

        Page<PaperContent> paperContentPage = new PageImpl<>(List.of());
        when(paperContentRepository.findAllByPaperIdIn(any(), any())).thenReturn(paperContentPage);

        // when
        Page<WritePartnershipResponseDTO> response = backofficePartnershipService.getPartnerships(pageable);

        // then
        assertThat(response.getContent()).hasSize(1);
        var dto = response.getContent().get(0);
        assertThat(dto.partnershipId()).isEqualTo(10L);
        assertThat(dto.storeName()).isEqualTo("역전할머니");
        assertThat(dto.adminName()).isEqualTo("숭실학생회");
    }
}
