package com.assu.server.domain.certification.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.service.AdminService;
import com.assu.server.domain.certification.component.CertificationSessionManager;
import com.assu.server.domain.certification.dto.CertificationGroupRequestDTO;
import com.assu.server.domain.certification.dto.CertificationPersonalRequestDTO;
import com.assu.server.domain.certification.dto.CertificationProgressResponseDTO;
import com.assu.server.domain.certification.dto.CertificationResponseDTO;
import com.assu.server.domain.certification.dto.GroupSessionRequest;
import com.assu.server.domain.certification.entity.AssociateCertification;
import com.assu.server.domain.certification.entity.enums.SessionStatus;
import com.assu.server.domain.certification.repository.AssociateCertificationRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.user.entity.Student;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

// AdminService 참조, 순환 참조 문제 주의
@Transactional
@Service
@RequiredArgsConstructor
public class CertificationServiceImpl implements CertificationService {
	private final StoreRepository storeRepository;
	private final AssociateCertificationRepository associateCertificationRepository;

	// 세션 메니저
	private final CertificationSessionManager sessionManager;
	// AdminService 참조
	private final AdminService adminService;
	private final SimpMessagingTemplate messagingTemplate;



	@Override
	public CertificationResponseDTO getSessionId(
		CertificationGroupRequestDTO dto, Member member){
		Long userId = member.getId();

		Long sessionId = sessionManager.openSession(dto.storeId(), dto.people());

		sessionManager.addUserToSession(sessionId, userId);

		return new CertificationResponseDTO(sessionId);

	}

	@Override
	public void handleCertification(GroupSessionRequest dto, Member member) {
		Long userId = member.getId();
		Long sessionId = dto.sessionId();

		if (!sessionManager.exists(sessionId)) {
			throw new GeneralException(ErrorStatus.NO_SUCH_SESSION);
		}

		Long storeId = Long.valueOf(sessionManager.getSessionInfo(sessionId, "storeId"));
		int targetPeople = Integer.parseInt(sessionManager.getSessionInfo(sessionId, "peopleNumber"));

		Student student = member.getStudentProfile();
		List<Admin> admins = adminService.findMatchingAdmins(student.getUniversity(), student.getDepartment(), student.getMajor());
		boolean matched = admins.stream().anyMatch(admin -> admin.getId().equals(dto.adminId()));

		if (!matched) {
			throw new IllegalArgumentException("학생과 매치되지 않는 정보입니다.");
		}

		if (sessionManager.hasUser(sessionId, userId)) {
			messagingTemplate.convertAndSend("/certification/progress/" + sessionId,
				new CertificationProgressResponseDTO("progress", null, "doubled member", sessionManager.snapshotUserIds(sessionId)));
			throw new GeneralException(ErrorStatus.DOUBLE_CERTIFIED_USER);
		}

		sessionManager.addUserToSession(sessionId, userId);
		List<Long> currentCertifiedUserIds = sessionManager.snapshotUserIds(sessionId);
		int currentCount = currentCertifiedUserIds.size();

		if (currentCount >= targetPeople) {
			Store store = storeRepository.findById(storeId).orElseThrow(
				() -> new GeneralException(ErrorStatus.NO_SUCH_STORE)
			);

			AssociateCertification certification = AssociateCertification.builder()
				.id(sessionId)
				.store(store)
				.peopleNumber(targetPeople)
				.isCertified(true)
				.status(SessionStatus.COMPLETED)
				.build();

			associateCertificationRepository.save(certification);

			messagingTemplate.convertAndSend("/certification/progress/" + sessionId,
				new CertificationProgressResponseDTO("completed", currentCount, "인증 완료", currentCertifiedUserIds));

			sessionManager.removeSession(sessionId);
		} else {
			messagingTemplate.convertAndSend("/certification/progress/" + sessionId,
				new CertificationProgressResponseDTO("progress", currentCount, null, currentCertifiedUserIds));
		}
	}
	@Override
	public void certificatePersonal(CertificationPersonalRequestDTO dto, Member member){
		// store id 추출
		Store store = storeRepository.findById(dto.storeId()).orElseThrow(
			() -> new GeneralException(ErrorStatus.NO_SUCH_STORE)
		);

		AssociateCertification personalCertificationData = dto.toPersonalCertification(store, member);
		associateCertificationRepository.save(personalCertificationData);
	}
}
