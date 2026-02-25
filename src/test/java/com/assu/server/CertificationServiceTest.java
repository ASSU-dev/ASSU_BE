package com.assu.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.service.AdminService;
import com.assu.server.domain.certification.component.CertificationSessionManager;
import com.assu.server.domain.certification.dto.CertificationProgressResponseDTO;
import com.assu.server.domain.certification.dto.GroupSessionRequest;
import com.assu.server.domain.certification.entity.AssociateCertification;
import com.assu.server.domain.certification.entity.enums.SessionStatus;
import com.assu.server.domain.certification.repository.AssociateCertificationRepository;
import com.assu.server.domain.certification.service.CertificationServiceImpl;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.user.entity.Student;
import com.assu.server.domain.user.entity.enums.Department;
import com.assu.server.domain.user.entity.enums.Major;
import com.assu.server.domain.user.entity.enums.University;

@ExtendWith(MockitoExtension.class)
class CertificationServiceImplTest {

	@InjectMocks
	private CertificationServiceImpl certificationService;

	@Mock
	private StoreRepository storeRepository;

	@Mock
	private AssociateCertificationRepository associateCertificationRepository;

	@Mock
	private CertificationSessionManager sessionManager;

	@Mock
	private AdminService adminService;

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@Test
	@DisplayName("마지막 인원이 인증을 완료하면 DB 저장 및 완료 메시지가 전송된다")
	void handleCertification_Complete_Success() {
		// 1. Given (준비)
		Long sessionId = 100L;
		Long userId = 1L;
		Long storeId = 500L;
		int targetPeople = 2;

		// 빌더를 사용하여 필요한 정보만 담은 실제 Student 객체 생성
		Student studentProfile = Student.builder()
			.university(University.SSU) // Enum 상수를 직접 사용
			.major(Major.COM)
			.department(Department.IT) // 필요한 경우 추가
			.build();

		Member mockMember = mock(Member.class);
		when(mockMember.getId()).thenReturn(userId);
		when(mockMember.getStudentProfile()).thenReturn(studentProfile);

		// DTO: adminId=77L, sessionId=100L
		GroupSessionRequest dto = new GroupSessionRequest(77L, sessionId);

		// 세션 매니저 설정
		when(sessionManager.exists(sessionId)).thenReturn(true);
		when(sessionManager.getSessionInfo(sessionId, "storeId")).thenReturn(String.valueOf(storeId));
		when(sessionManager.getSessionInfo(sessionId, "peopleNumber")).thenReturn(String.valueOf(targetPeople));

		// 학적 매칭 성공 설정
		Admin matchingAdmin = Admin.builder().id(77L).build();
		when(adminService.findMatchingAdmins(eq(University.SSU), any(), eq(Major.COM)))
			.thenReturn(List.of(matchingAdmin));

		// 중복 체크 및 인원 상태 설정
		when(sessionManager.hasUser(sessionId, userId)).thenReturn(false);
		when(sessionManager.snapshotUserIds(sessionId)).thenReturn(List.of(1L, 2L)); // 본인 포함 2명

		// Store 객체 (Partner 포함 여부에 따라 Mock 혹은 Builder 사용)
		Store mockStore = Store.builder().id(storeId).build();
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));

		// 2. When (실행)
		assertDoesNotThrow(() -> certificationService.handleCertification(dto, mockMember));

		// 3. Then (검증)
		// - AssociateCertification 저장 확인
		verify(associateCertificationRepository, times(1)).save(any());

		// - Redis 세션 삭제 확인
		verify(sessionManager, times(1)).removeSession(sessionId);

		// - 메시지 전송 확인 (DTO 필드명 type과 status 혼동 주의, 현재 DTO상 type이 'completed'여야 함)
		verify(messagingTemplate).convertAndSend(
			eq("/certification/progress/" + sessionId),
			argThat((CertificationProgressResponseDTO resp) -> "completed".equals(resp.type()))
		);
	}

	@Test
	@DisplayName("참여자가 인증하면 대표자가 구독 중인 경로로 실시간 진행 상태가 전송된다")
	void verify_websocket_message_destination_and_data() {
		// 1. Given (상황 설정)
		Long sessionId = 100L;
		Long participantId = 2L; // 참여자 ID
		GroupSessionRequest dto = new GroupSessionRequest(77L, sessionId);

		// 가짜 참여자 멤버 설정
		Member participantMember = mock(Member.class);
		Student mockStudent = mock(Student.class);
		when(participantMember.getId()).thenReturn(participantId);
		when(participantMember.getStudentProfile()).thenReturn(mockStudent);

		// 세션 정보 설정 (정원 4명인 방이라고 가정)
		when(sessionManager.exists(sessionId)).thenReturn(true);
		when(sessionManager.getSessionInfo(sessionId, "storeId")).thenReturn("500");
		when(sessionManager.getSessionInfo(sessionId, "peopleNumber")).thenReturn("4");

		// 학적 인증 통과 설정
		Admin matchingAdmin = Admin.builder().id(77L).build();
		when(adminService.findMatchingAdmins(any(), any(), any())).thenReturn(List.of(matchingAdmin));

		// 현재 인증된 유저 리스트 (이미 1명 있고, 이번에 참여자가 추가되어 총 2명이 된 상황)
		List<Long> currentUsers = List.of(1L, participantId);
		when(sessionManager.snapshotUserIds(sessionId)).thenReturn(currentUsers);

		// 2. When (실행)
		certificationService.handleCertification(dto, participantMember);

		// 3. Then (구독 경로 및 데이터 검증)
		// - 첫 번째 인자: 구독 경로가 "/certification/progress/100"인지 확인
		// - 두 번째 인자: 전달된 DTO의 내용이 'progress'이고 인원수가 2명인지 확인
		verify(messagingTemplate).convertAndSend(
			eq("/certification/progress/" + sessionId),
			argThat((CertificationProgressResponseDTO resp) -> {
				return resp.type().equals("progress") &&
					resp.count() == 2 &&
					resp.userIds().contains(participantId);
			})
		);
	}

	@Test
	@DisplayName("정원이 충족되면 세션을 삭제하고, 상태가 COMPLETED인 인증 정보를 저장한다")
	void handleCertification_SessionClose_And_SaveEntity() {
		// 1. Given (준비)
		Long sessionId = 100L;
		Long storeId = 500L;
		int targetPeople = 2;

		// Student 및 Member 설정
		Student studentProfile = Student.builder()
			.university(University.SSU)
			.major(Major.COM)
			.build();
		Member mockMember = mock(Member.class);
		when(mockMember.getId()).thenReturn(1L);
		when(mockMember.getStudentProfile()).thenReturn(studentProfile);

		// DTO 설정
		GroupSessionRequest dto = new GroupSessionRequest(77L, sessionId);

		// 서비스 내부 로직 흐름 Mocking
		when(sessionManager.exists(sessionId)).thenReturn(true);
		when(sessionManager.getSessionInfo(sessionId, "storeId")).thenReturn(String.valueOf(storeId));
		when(sessionManager.getSessionInfo(sessionId, "peopleNumber")).thenReturn(String.valueOf(targetPeople));

		Admin matchingAdmin = Admin.builder().id(77L).build();
		when(adminService.findMatchingAdmins(any(), any(), any())).thenReturn(List.of(matchingAdmin));
		when(sessionManager.hasUser(sessionId, 1L)).thenReturn(false);

		// 마지막 인원이 참여하여 정원이 찬 상황 가정
		when(sessionManager.snapshotUserIds(sessionId)).thenReturn(List.of(1L, 2L));

		Store mockStore = Store.builder().id(storeId).build();
		when(storeRepository.findById(storeId)).thenReturn(Optional.of(mockStore));

		// 2. When (실행)
		certificationService.handleCertification(dto, mockMember);

		// 3. Then (상세 검증)

		// A. 세션 매니저의 removeSession이 호출되었는지 (세션 종료 확인)
		verify(sessionManager, times(1)).removeSession(sessionId);

		// B. AssociateCertification 저장 내용 검증 (ArgumentCaptor 활용)
		ArgumentCaptor<AssociateCertification> captor = ArgumentCaptor.forClass(AssociateCertification.class);
		verify(associateCertificationRepository).save(captor.capture());

		AssociateCertification savedEntity = captor.getValue();

		// 엔티티 필드값 검증
		assertEquals(SessionStatus.COMPLETED, savedEntity.getStatus()); // 상태가 COMPLETED인가?
		assertTrue(savedEntity.getIsCertified()); // 인증 여부가 true인가?
		assertEquals(targetPeople, savedEntity.getPeopleNumber()); // 정원 정보가 일치하는가?
		assertEquals(mockStore, savedEntity.getStore()); // 연결된 상점 정보가 맞는가?
	}
}