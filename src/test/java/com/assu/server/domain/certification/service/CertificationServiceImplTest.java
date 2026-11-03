package com.assu.server.domain.certification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.service.AdminService;
import com.assu.server.domain.certification.component.CertificationSessionManager;
import com.assu.server.domain.certification.dto.CertificationGroupRequestDTO;
import com.assu.server.domain.certification.dto.CertificationResponseDTO;
import com.assu.server.domain.certification.dto.CertificationSessionResponseDTO;
import com.assu.server.domain.certification.repository.AssociateCertificationRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class CertificationServiceImplTest {

	@Mock
	private StoreRepository storeRepository;
	@Mock
	private AssociateCertificationRepository associateCertificationRepository;
	@Mock
	private PaperContentRepository paperContentRepository;
	@Mock
	private CertificationSessionManager sessionManager;
	@Mock
	private AdminService adminService;
	@Mock
	private SimpMessagingTemplate messagingTemplate;

	private CertificationServiceImpl certificationService;

	@BeforeEach
	void setUp() {
		certificationService = new CertificationServiceImpl(
			storeRepository,
			paperContentRepository,
			associateCertificationRepository,
			sessionManager,
			adminService,
			messagingTemplate
		);
	}

	private PaperContent contentOf(Long storeId, Long adminId) {
		Store store = mock(Store.class);
		when(store.getId()).thenReturn(storeId);
		Admin admin = mock(Admin.class);
		when(admin.getId()).thenReturn(adminId);
		Paper paper = mock(Paper.class);
		when(paper.getStore()).thenReturn(store);
		when(paper.getAdmin()).thenReturn(admin);
		PaperContent content = mock(PaperContent.class);
		when(content.getPaper()).thenReturn(paper);
		return content;
	}

	@Test
	void getSessionId_whenContentIdMatches_thenStoresAdminIdAndContentId() {
		// given
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(10L);
		PaperContent content = contentOf(7L, 45L);
		when(paperContentRepository.findById(3L)).thenReturn(Optional.of(content));
		when(sessionManager.openSession(7L, 4, 10L, 45L, 3L)).thenReturn(123L);
		CertificationGroupRequestDTO dto = new CertificationGroupRequestDTO(4, 7L, 45L, 1, 3L);

		// when
		CertificationResponseDTO result = certificationService.getSessionId(dto, member);

		// then
		assertThat(result.sessionId()).isEqualTo(123L);
		verify(sessionManager).openSession(7L, 4, 10L, 45L, 3L);
	}

	@Test
	void getSessionId_whenContentIdIsNull_thenSkipsContentValidation() {
		// given
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(10L);
		when(sessionManager.openSession(7L, 4, 10L, 45L, null)).thenReturn(123L);
		CertificationGroupRequestDTO dto = new CertificationGroupRequestDTO(4, 7L, 45L, 1, null);

		// when
		CertificationResponseDTO result = certificationService.getSessionId(dto, member);

		// then
		assertThat(result.sessionId()).isEqualTo(123L);
		verify(paperContentRepository, never()).findById(any());
	}

	@Test
	void getSessionId_whenContentBelongsToOtherStore_thenThrowsNoSuchContent() {
		// given
		Member member = mock(Member.class);
		PaperContent content = contentOf(99L, 45L);
		when(paperContentRepository.findById(3L)).thenReturn(Optional.of(content));
		CertificationGroupRequestDTO dto = new CertificationGroupRequestDTO(4, 7L, 45L, 1, 3L);

		// when & then
		assertThatThrownBy(() -> certificationService.getSessionId(dto, member))
			.isInstanceOf(GeneralException.class)
			.extracting(e -> ((GeneralException)e).getCode())
			.isEqualTo(ErrorStatus.NO_SUCH_CONTENT);
		verify(sessionManager, never()).openSession(any(), any(), any(), any(), any());
	}

	@Test
	void getSession_whenSessionNotExists_thenThrowsNoSuchSession() {
		// given
		when(sessionManager.exists(1L)).thenReturn(false);

		// when & then
		assertThatThrownBy(() -> certificationService.getSession(1L))
			.isInstanceOf(GeneralException.class)
			.extracting(e -> ((GeneralException)e).getCode())
			.isEqualTo(ErrorStatus.NO_SUCH_SESSION);
	}

	@Test
	void getSession_whenContentIdIsMissing_thenReturnsNullPartnershipContent() {
		// given
		Store store = mock(Store.class);
		when(store.getId()).thenReturn(7L);
		when(store.getName()).thenReturn("숭실카페");
		when(sessionManager.exists(123L)).thenReturn(true);
		when(sessionManager.getSessionInfo(123L, "storeId")).thenReturn("7");
		when(sessionManager.getSessionInfo(123L, "peopleNumber")).thenReturn("4");
		when(sessionManager.getSessionInfo(123L, "contentId")).thenReturn(null);
		when(sessionManager.snapshotUserIds(123L)).thenReturn(List.of(10L, 11L));
		when(storeRepository.findById(7L)).thenReturn(Optional.of(store));

		// when
		CertificationSessionResponseDTO result = certificationService.getSession(123L);

		// then
		assertThat(result.sessionId()).isEqualTo(123L);
		assertThat(result.storeName()).isEqualTo("숭실카페");
		assertThat(result.peopleNumber()).isEqualTo(4);
		assertThat(result.currentCount()).isEqualTo(2);
		assertThat(result.partnershipContent()).isNull();
	}
}
