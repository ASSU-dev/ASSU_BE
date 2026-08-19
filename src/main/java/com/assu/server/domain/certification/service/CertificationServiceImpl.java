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
import com.assu.server.domain.student.entity.Student;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// AdminService 참조, 순환 참조 문제 주의
@Transactional
@Service
@RequiredArgsConstructor
@Slf4j
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

		Long sessionId = sessionManager.openSession(dto.storeId(), dto.people(), userId);

		sessionManager.addUserToSession(sessionId, userId);

		return new CertificationResponseDTO(sessionId);

	}

	@Override
	public void expireSession(Long sessionId, Member member) {
		if (!sessionManager.exists(sessionId)) {
			throw new GeneralException(ErrorStatus.NO_SUCH_SESSION);
		}

		Long ownerId = Long.valueOf(sessionManager.getSessionInfo(sessionId, "ownerId"));
		if (!ownerId.equals(member.getId())) {
			throw new GeneralException(ErrorStatus._FORBIDDEN);
		}

		List<Long> certifiedUserIds = sessionManager.snapshotUserIds(sessionId);
		sessionManager.removeSession(sessionId);

		messagingTemplate.convertAndSend("/certification/progress/" + sessionId,
			new CertificationProgressResponseDTO(
				"expired",
				certifiedUserIds.size(),
				"인증 세션이 만료되었습니다.",
				certifiedUserIds
			));
		log.info("인증 세션 만료 이벤트 전송 - sessionId: {}, certifiedCount: {}, destination: /certification/progress/{}",
			sessionId, certifiedUserIds.size(), sessionId);
	}

	@Override
	public CertificationProgressResponseDTO handleCertification(GroupSessionRequest dto, Member member) {
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
			List<Long> currentCertifiedUserIds = sessionManager.snapshotUserIds(sessionId);
			CertificationProgressResponseDTO response = new CertificationProgressResponseDTO(
				"mismatch",
				currentCertifiedUserIds.size(),
				"학생과 매치되지 않는 정보입니다.",
				currentCertifiedUserIds
			);
			messagingTemplate.convertAndSend("/certification/progress/" + sessionId, response);
			return response;
		}

		if (sessionManager.hasUser(sessionId, userId)) {
			List<Long> currentCertifiedUserIds = sessionManager.snapshotUserIds(sessionId);
			CertificationProgressResponseDTO response = new CertificationProgressResponseDTO(
				"progress",
				null,
				"doubled member",
				currentCertifiedUserIds
			);
			messagingTemplate.convertAndSend("/certification/progress/" + sessionId, response);
			throw new GeneralException(ErrorStatus.DOUBLE_CERTIFIED_USER);
		}

		sessionManager.addUserToSession(sessionId, userId);
		List<Long> currentCertifiedUserIds = sessionManager.snapshotUserIds(sessionId);
		int currentCount = currentCertifiedUserIds.size();

		CertificationProgressResponseDTO response;
		if (currentCount >= targetPeople) {
			Store store = storeRepository.findById(storeId).orElseThrow(
				() -> new GeneralException(ErrorStatus.NO_SUCH_STORE)
			);

			AssociateCertification certification = AssociateCertification.builder()
				.store(store)
				.peopleNumber(targetPeople)
				.isCertified(true)
				.status(SessionStatus.COMPLETED)
				.build();

			associateCertificationRepository.save(certification);

			response = new CertificationProgressResponseDTO("completed", currentCount, "인증 완료", currentCertifiedUserIds);
			messagingTemplate.convertAndSend("/certification/progress/" + sessionId, response);

			sessionManager.removeSession(sessionId);
		} else {
			response = new CertificationProgressResponseDTO("progress", currentCount, null, currentCertifiedUserIds);
			messagingTemplate.convertAndSend("/certification/progress/" + sessionId, response);
		}
		return response;
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
