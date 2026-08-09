package com.assu.server.domain.partnership.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.chat.entity.ChattingRoom;
import com.assu.server.domain.chat.repository.ChatRepository;
import com.assu.server.domain.chat.service.ChatService;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.partnership.dto.AdminPartnershipCheckResponseDTO;
import com.assu.server.domain.partnership.dto.PartnershipFinalRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipStatusUpdateRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipStatusUpdateResponseDTO;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.GoodsRepository;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.entity.PartnershipUsage;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.PartnershipUsageRepository;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.infra.s3.AmazonS3Manager;

@ExtendWith(MockitoExtension.class)
class PartnershipServiceImplTest {

	private PartnershipServiceImpl partnershipService;

	@Mock
	private PartnershipUsageRepository partnershipUsageRepository;

	@Mock
	private StudentRepository studentRepository;

	@Mock
	private PaperContentRepository contentRepository;

	@Mock
	private NotificationCommandService notificationService;

	@Mock
	private ChatService chatService;

	@Mock
	private ChatRepository chatRepository;

	@Mock
	private PaperRepository paperRepository;

	@Mock
	private PaperContentRepository paperContentRepository;

	@Mock
	private GoodsRepository goodsRepository;

	@Mock
	private AdminRepository adminRepository;

	@Mock
	private PartnerRepository partnerRepository;

	@Mock
	private StoreRepository storeRepository;

	@Mock
	private AmazonS3Manager amazonS3Manager;

	private static final Long ADMIN_ID = 10L;
	private static final Long PARTNER_ID = 20L;
	private static final Long PAPER_ID = 100L;

	@BeforeEach
	void setUp() {
		// contentRepository와 paperContentRepository가 같은 타입이라 명시적으로 생성자 주입
		partnershipService = new PartnershipServiceImpl(
			partnershipUsageRepository, studentRepository, contentRepository,
			notificationService, chatService, chatRepository,
			paperRepository, paperContentRepository, goodsRepository,
			adminRepository, partnerRepository, storeRepository, amazonS3Manager);
	}

	private Admin admin() {
		return Admin.builder().id(ADMIN_ID).name("총학생회").isPhoneVerified(false).build();
	}

	private Partner partner() {
		return Partner.builder().id(PARTNER_ID).name("역전할머니 맥주").isPhoneVerified(false)
			.address("서울특별시 동작구").build();
	}

	// ===== recordPartnershipUsage =====

	@Test
	@DisplayName("존재하지 않는 제휴 내용으로 이용 기록을 남기면 NO_SUCH_CONTENT 예외가 발생한다")
	void recordPartnershipUsage_ContentNotFound_ThrowsException() {
		// 1. Given
		PartnershipFinalRequestDTO request = new PartnershipFinalRequestDTO(
			500L, ADMIN_ID, "03", "총학생회", "역전할머니 맥주", "생맥주 1잔 무료", 1000L, null, List.of());
		when(contentRepository.findById(1000L)).thenReturn(Optional.empty());

		Member member = mock(Member.class);

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> partnershipService.recordPartnershipUsage(request, member));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_CONTENT, exception.getCode());
		verify(partnershipUsageRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("제휴 이용 기록 시 참여 학생 전원의 스탬프가 적립되고 이용 내역 저장 후 주문 알림이 전송된다")
	void recordPartnershipUsage_Success_StampsAndNotifies() {
		// 1. Given (요청자 1L + 동행 2L, 중복 ID는 Set으로 제거됨)
		PartnershipFinalRequestDTO request = new PartnershipFinalRequestDTO(
			500L, ADMIN_ID, "03", "총학생회", "역전할머니 맥주", "생맥주 1잔 무료", 1000L, null, List.of(1L, 2L));

		Paper paper = Paper.builder().id(PAPER_ID).build();
		PaperContent content = PaperContent.builder().id(1000L).paper(paper).build();
		when(contentRepository.findById(1000L)).thenReturn(Optional.of(content));

		Member member = mock(Member.class);
		when(member.getId()).thenReturn(1L);

		Student student1 = Student.builder().university(University.SSU).stamp(0).build();
		Student student2 = Student.builder().university(University.SSU).stamp(9).build();
		when(studentRepository.findAllById(any())).thenReturn(List.of(student1, student2));

		Partner storePartner = partner();
		Store store = Store.builder().id(500L).partner(storePartner).build();
		when(storeRepository.findById(500L)).thenReturn(Optional.of(store));

		// 2. When
		partnershipService.recordPartnershipUsage(request, member);

		// 3. Then (스탬프 적립 확인)
		assertEquals(1, student1.getStamp());
		assertEquals(10, student2.getStamp());

		// 이용 내역 저장 내용 확인
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<PartnershipUsage>> usageCaptor = ArgumentCaptor.forClass(List.class);
		verify(partnershipUsageRepository, times(1)).saveAll(usageCaptor.capture());
		assertEquals(2, usageCaptor.getValue().size());
		assertEquals(PAPER_ID, usageCaptor.getValue().get(0).getPaperId());

		// 파트너에게 주문 알림 전송 확인
		verify(notificationService, times(1)).sendOrder(PARTNER_ID, 0L, "03", "생맥주 1잔 무료");
	}

	// ===== updatePartnershipStatus =====

	@Test
	@DisplayName("접근 권한이 없는 회원이 제안서 상태를 변경하면 _FORBIDDEN 예외가 발생한다")
	void updatePartnershipStatus_NoAccess_ThrowsException() {
		// 1. Given (다른 관리자의 paper)
		Paper paper = Paper.builder().id(PAPER_ID).admin(admin()).partner(partner()).build();
		when(paperRepository.findById(PAPER_ID)).thenReturn(Optional.of(paper));

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> partnershipService.updatePartnershipStatus(
				PAPER_ID, new PartnershipStatusUpdateRequestDTO("ACTIVE"), 999L, UserRole.ADMIN));

		// 3. Then
		assertEquals(ErrorStatus._FORBIDDEN, exception.getCode());
	}

	@Test
	@DisplayName("잘못된 상태 문자열로 변경을 요청하면 _BAD_REQUEST 예외가 발생한다")
	void updatePartnershipStatus_InvalidStatus_ThrowsException() {
		// 1. Given
		Paper paper = Paper.builder().id(PAPER_ID).admin(admin()).partner(partner()).build();
		when(paperRepository.findById(PAPER_ID)).thenReturn(Optional.of(paper));

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> partnershipService.updatePartnershipStatus(
				PAPER_ID, new PartnershipStatusUpdateRequestDTO("NOT_A_STATUS"), ADMIN_ID, UserRole.ADMIN));

		// 3. Then
		assertEquals(ErrorStatus._BAD_REQUEST, exception.getCode());
	}

	@Test
	@DisplayName("제안서를 ACTIVE로 변경하면 상태가 바뀌고 파트너에게 성사 안내 메시지와 알림이 전송된다")
	void updatePartnershipStatus_ToActive_SendsGuideMessage() {
		// 1. Given
		Admin admin = admin();
		Partner partner = partner();
		Paper paper = Paper.builder()
			.id(PAPER_ID).admin(admin).partner(partner).isActivated(ActivationStatus.SUSPEND).build();
		when(paperRepository.findById(PAPER_ID)).thenReturn(Optional.of(paper));
		when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

		ChattingRoom room = ChattingRoom.builder().id(1L).admin(admin).partner(partner).build();
		when(chatRepository.findChattingRoomByAdminIdAndPartnerId(ADMIN_ID, PARTNER_ID)).thenReturn(room);

		// 2. When
		PartnershipStatusUpdateResponseDTO response = partnershipService.updatePartnershipStatus(
			PAPER_ID, new PartnershipStatusUpdateRequestDTO("active"), ADMIN_ID, UserRole.ADMIN);

		// 3. Then
		assertEquals("SUSPEND", response.prevStatus());
		assertEquals("ACTIVE", response.newStatus());
		assertEquals(ActivationStatus.ACTIVE, paper.getIsActivated());

		verify(chatService, times(1)).sendGuideMessage(any());
		verify(notificationService, times(1))
			.sendChat(eq(PARTNER_ID), eq(1L), eq("총학생회"), anyString());
	}

	// ===== checkPartnershipWithPartner =====

	@Test
	@DisplayName("존재하지 않는 파트너와의 제휴 여부를 확인하면 NO_SUCH_PARTNER 예외가 발생한다")
	void checkPartnershipWithPartner_PartnerNotFound_ThrowsException() {
		// 1. Given
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> partnershipService.checkPartnershipWithPartner(ADMIN_ID, PARTNER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_PARTNER, exception.getCode());
	}

	@Test
	@DisplayName("제휴 이력이 없으면 isPartnered=false, 상태 NONE으로 반환한다")
	void checkPartnershipWithPartner_NotPartnered_ReturnsNone() {
		// 1. Given
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner()));
		when(paperRepository.existsByAdmin_IdAndPartner_IdAndIsActivatedIn(eq(ADMIN_ID), eq(PARTNER_ID), anyList()))
			.thenReturn(false);

		// 2. When
		AdminPartnershipCheckResponseDTO response =
			partnershipService.checkPartnershipWithPartner(ADMIN_ID, PARTNER_ID);

		// 3. Then
		assertFalse(response.isPartnered());
		assertEquals("NONE", response.status());
		assertNull(response.paperId());
	}

	@Test
	@DisplayName("제휴 중이면 최신 제안서의 ID와 상태를 반환한다")
	void checkPartnershipWithPartner_Partnered_ReturnsLatestPaper() {
		// 1. Given
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner()));
		when(paperRepository.existsByAdmin_IdAndPartner_IdAndIsActivatedIn(eq(ADMIN_ID), eq(PARTNER_ID), anyList()))
			.thenReturn(true);

		Paper latest = Paper.builder().id(PAPER_ID).isActivated(ActivationStatus.ACTIVE).build();
		when(paperRepository.findTopByAdmin_IdAndPartner_IdAndIsActivatedInOrderByIdDesc(
			eq(ADMIN_ID), eq(PARTNER_ID), anyList())).thenReturn(Optional.of(latest));

		// 2. When
		AdminPartnershipCheckResponseDTO response =
			partnershipService.checkPartnershipWithPartner(ADMIN_ID, PARTNER_ID);

		// 3. Then
		assertTrue(response.isPartnered());
		assertEquals(PAPER_ID, response.paperId());
		assertEquals("ACTIVE", response.status());
	}

	// ===== deletePartnership =====

	@Test
	@DisplayName("파트너 없는 수기 제휴를 삭제할 때 임시 매장을 참조하는 제안서가 없으면 매장도 함께 삭제된다")
	void deletePartnership_TempStoreWithoutRefs_DeletesStore() {
		// 1. Given (partner가 null인 수기 제휴)
		Store tempStore = Store.builder().id(500L).name("임시매장").build();
		Paper paper = Paper.builder().id(PAPER_ID).admin(admin()).partner(null).store(tempStore).build();
		when(paperRepository.findById(PAPER_ID)).thenReturn(Optional.of(paper));
		when(paperContentRepository.findByPaperId(PAPER_ID)).thenReturn(List.of());
		when(paperRepository.countByStore_Id(500L)).thenReturn(0L);

		// 2. When
		partnershipService.deletePartnership(PAPER_ID, ADMIN_ID, UserRole.ADMIN);

		// 3. Then
		verify(paperRepository, times(1)).delete(paper);
		verify(storeRepository, times(1)).delete(tempStore);
	}

	@Test
	@DisplayName("임시 매장을 다른 제안서가 참조 중이면 매장은 삭제하지 않는다")
	void deletePartnership_TempStoreStillReferenced_KeepsStore() {
		// 1. Given
		Store tempStore = Store.builder().id(500L).name("임시매장").build();
		Paper paper = Paper.builder().id(PAPER_ID).admin(admin()).partner(null).store(tempStore).build();
		when(paperRepository.findById(PAPER_ID)).thenReturn(Optional.of(paper));
		when(paperContentRepository.findByPaperId(PAPER_ID)).thenReturn(List.of());
		when(paperRepository.countByStore_Id(500L)).thenReturn(1L);

		// 2. When
		partnershipService.deletePartnership(PAPER_ID, ADMIN_ID, UserRole.ADMIN);

		// 3. Then
		verify(paperRepository, times(1)).delete(paper);
		verify(storeRepository, never()).delete(any(Store.class));
	}

	@Test
	@DisplayName("제안서 삭제 시 연결된 제휴 내용과 상품이 먼저 삭제된다")
	void deletePartnership_WithContents_DeletesContentsAndGoods() {
		// 1. Given
		Partner partner = partner();
		Store store = Store.builder().id(500L).partner(partner).build();
		Paper paper = Paper.builder().id(PAPER_ID).admin(admin()).partner(partner).store(store).build();
		when(paperRepository.findById(PAPER_ID)).thenReturn(Optional.of(paper));

		PaperContent content = PaperContent.builder().id(1000L).paper(paper).build();
		when(paperContentRepository.findByPaperId(PAPER_ID)).thenReturn(List.of(content));

		// 2. When
		partnershipService.deletePartnership(PAPER_ID, ADMIN_ID, UserRole.ADMIN);

		// 3. Then
		verify(goodsRepository, times(1)).deleteAllByContentIds(List.of(1000L));
		verify(paperContentRepository, times(1)).deleteAll(List.of(content));
		verify(paperRepository, times(1)).delete(paper);
		// partner가 있는 제휴이므로 매장은 삭제되지 않음
		verify(storeRepository, never()).delete(any(Store.class));
	}
}
