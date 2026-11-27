package com.assu.server.domain.certification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.service.AdminService;
import com.assu.server.domain.certification.component.CertificationSessionManager;
import com.assu.server.domain.certification.dto.CertificationGroupRequestDTO;
import com.assu.server.domain.certification.dto.CertificationResponseDTO;
import com.assu.server.domain.certification.dto.CertificationSessionResponseDTO;
import com.assu.server.domain.certification.entity.AssociateCertification;
import com.assu.server.domain.certification.repository.AssociateCertificationRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.global.util.WebSocketBroadcaster;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

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
	private WebSocketBroadcaster webSocketBroadcaster;
	@Mock
	private StudentRepository studentRepository;

	private CertificationServiceImpl certificationService;

	@BeforeEach
	void setUp() {
		certificationService = new CertificationServiceImpl(
			storeRepository,
			paperContentRepository,
			associateCertificationRepository,
			new SimpleMeterRegistry(),
			sessionManager,
			adminService,
			webSocketBroadcaster,
			studentRepository
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

	private static final Long REQUESTER_ID = 1L;
	private static final Long STORE_ID = 7L;
	private static final Long SESSION_ID = 123L;

	private AssociateCertification certificationOf(Long id, Long studentId, Long sessionId, LocalDateTime usedAt) {
		return AssociateCertification.builder()
			.id(id)
			.student(Student.builder().id(studentId).build())
			.sessionId(sessionId)
			.usedAt(usedAt)
			.isCertified(true)
			.build();
	}

	@Test
	void claimCertifiedParticipants_whenRequesterHasNoRecentCertification_thenThrowsUsageCertificationRequired() {
		// given
		when(associateCertificationRepository.findRecentByStudentIdAndStoreId(eq(REQUESTER_ID), eq(STORE_ID), any()))
			.thenReturn(List.of());

		// when & then
		assertThatThrownBy(() -> certificationService.claimCertifiedParticipants(REQUESTER_ID, STORE_ID, null, List.of(2L)))
			.isInstanceOf(GeneralException.class)
			.extracting(e -> ((GeneralException)e).getCode())
			.isEqualTo(ErrorStatus.USAGE_CERTIFICATION_REQUIRED);
		verify(associateCertificationRepository, never()).markUsedIfUnused(anyLong(), any());
	}

	@Test
	void claimCertifiedParticipants_whenPersonalCertification_thenClaimsOnlyRequesterAndIgnoresUserIds() {
		// given
		AssociateCertification personal = certificationOf(10L, REQUESTER_ID, null, null);
		when(associateCertificationRepository.findRecentByStudentIdAndStoreId(eq(REQUESTER_ID), eq(STORE_ID), any()))
			.thenReturn(List.of(personal));
		when(associateCertificationRepository.markUsedIfUnused(eq(10L), any())).thenReturn(1);

		// when
		List<Long> result = certificationService.claimCertifiedParticipants(REQUESTER_ID, STORE_ID, null, List.of(2L, 3L));

		// then
		assertThat(result).containsExactly(REQUESTER_ID);
		verify(associateCertificationRepository, never()).findRecentBySessionIdAndStoreId(any(), any(), any());
	}

	@Test
	void claimCertifiedParticipants_whenGroupCertificationWithoutSessionId_thenClaimsOnlyRequestedUnusedSessionParticipants() {
		// given
		AssociateCertification anchor = certificationOf(10L, REQUESTER_ID, SESSION_ID, null);
		AssociateCertification requested = certificationOf(11L, 2L, SESSION_ID, null);
		AssociateCertification alreadyUsed = certificationOf(12L, 3L, SESSION_ID, LocalDateTime.now());
		AssociateCertification notRequested = certificationOf(13L, 4L, SESSION_ID, null);
		when(associateCertificationRepository.findRecentByStudentIdAndStoreId(eq(REQUESTER_ID), eq(STORE_ID), any()))
			.thenReturn(List.of(anchor));
		when(associateCertificationRepository.findRecentBySessionIdAndStoreId(eq(SESSION_ID), eq(STORE_ID), any()))
			.thenReturn(List.of(anchor, requested, alreadyUsed, notRequested));
		when(associateCertificationRepository.markUsedIfUnused(eq(10L), any())).thenReturn(1);
		when(associateCertificationRepository.markUsedIfUnused(eq(11L), any())).thenReturn(1);

		// when
		List<Long> result = certificationService.claimCertifiedParticipants(
			REQUESTER_ID, STORE_ID, null, List.of(REQUESTER_ID, 2L, 3L, 99L));

		// then
		assertThat(result).containsExactly(REQUESTER_ID, 2L);
		verify(associateCertificationRepository, never()).markUsedIfUnused(eq(12L), any());
		verify(associateCertificationRepository, never()).markUsedIfUnused(eq(13L), any());
	}

	@Test
	void claimCertifiedParticipants_whenRecentCertificationsAlreadyUsed_thenReturnsEmptyWithoutClaiming() {
		// given
		AssociateCertification used = certificationOf(10L, REQUESTER_ID, null, LocalDateTime.now());
		when(associateCertificationRepository.findRecentByStudentIdAndStoreId(eq(REQUESTER_ID), eq(STORE_ID), any()))
			.thenReturn(List.of(used));

		// when
		List<Long> result = certificationService.claimCertifiedParticipants(REQUESTER_ID, STORE_ID, null, null);

		// then
		assertThat(result).isEmpty();
		verify(associateCertificationRepository, never()).markUsedIfUnused(anyLong(), any());
	}

	@Test
	void claimCertifiedParticipants_whenConcurrentRequestClaimedFirst_thenReturnsEmpty() {
		// given
		AssociateCertification group = certificationOf(10L, REQUESTER_ID, SESSION_ID, null);
		when(associateCertificationRepository.findRecentByStudentIdAndStoreId(eq(REQUESTER_ID), eq(STORE_ID), any()))
			.thenReturn(List.of(group));
		when(associateCertificationRepository.findRecentBySessionIdAndStoreId(eq(SESSION_ID), eq(STORE_ID), any()))
			.thenReturn(List.of(group, certificationOf(11L, 2L, SESSION_ID, null)));
		when(associateCertificationRepository.markUsedIfUnused(eq(10L), any())).thenReturn(0);

		// when
		List<Long> result = certificationService.claimCertifiedParticipants(REQUESTER_ID, STORE_ID, null, List.of(2L));

		// then
		assertThat(result).isEmpty();
		verify(associateCertificationRepository, never()).markUsedIfUnused(eq(11L), any());
	}

	@Test
	void claimCertifiedParticipants_whenSessionIdGiven_thenClaimsAllSessionParticipantsIgnoringUserIds() {
		// given
		when(associateCertificationRepository.findRecentBySessionIdAndStoreId(eq(SESSION_ID), eq(STORE_ID), any()))
			.thenReturn(List.of(
				certificationOf(11L, 2L, SESSION_ID, null),
				certificationOf(10L, REQUESTER_ID, SESSION_ID, null),
				certificationOf(12L, 3L, SESSION_ID, null)));
		when(associateCertificationRepository.markUsedIfUnused(anyLong(), any())).thenReturn(1);

		// when
		List<Long> result = certificationService.claimCertifiedParticipants(REQUESTER_ID, STORE_ID, SESSION_ID, List.of(2L));

		// then
		assertThat(result).containsExactly(REQUESTER_ID, 2L, 3L);
		verify(associateCertificationRepository, never()).findRecentByStudentIdAndStoreId(any(), any(), any());
	}

	@Test
	void claimCertifiedParticipants_whenSessionIdGivenAndRequesterNotParticipant_thenThrowsUsageCertificationRequired() {
		// given
		when(associateCertificationRepository.findRecentBySessionIdAndStoreId(eq(SESSION_ID), eq(STORE_ID), any()))
			.thenReturn(List.of(certificationOf(11L, 2L, SESSION_ID, null)));

		// when & then
		assertThatThrownBy(() -> certificationService.claimCertifiedParticipants(REQUESTER_ID, STORE_ID, SESSION_ID, null))
			.isInstanceOf(GeneralException.class)
			.extracting(e -> ((GeneralException)e).getCode())
			.isEqualTo(ErrorStatus.USAGE_CERTIFICATION_REQUIRED);
		verify(associateCertificationRepository, never()).markUsedIfUnused(anyLong(), any());
	}

	@Test
	void claimCertifiedParticipants_whenSessionIdGivenAndAlreadyClaimed_thenReturnsEmpty() {
		// given
		when(associateCertificationRepository.findRecentBySessionIdAndStoreId(eq(SESSION_ID), eq(STORE_ID), any()))
			.thenReturn(List.of(
				certificationOf(10L, REQUESTER_ID, SESSION_ID, LocalDateTime.now()),
				certificationOf(11L, 2L, SESSION_ID, LocalDateTime.now())));

		// when
		List<Long> result = certificationService.claimCertifiedParticipants(REQUESTER_ID, STORE_ID, SESSION_ID, null);

		// then
		assertThat(result).isEmpty();
		verify(associateCertificationRepository, never()).markUsedIfUnused(anyLong(), any());
	}
}
