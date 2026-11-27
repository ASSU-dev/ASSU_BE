package com.assu.server.domain.certification.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.assu.server.global.util.WebSocketBroadcaster;
import org.springframework.stereotype.Service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.service.AdminService;
import com.assu.server.domain.certification.component.CertificationSessionManager;
import com.assu.server.domain.certification.dto.CertificationGroupRequestDTO;
import com.assu.server.domain.certification.dto.CertificationPersonalRequestDTO;
import com.assu.server.domain.certification.dto.CertificationProgressResponseDTO;
import com.assu.server.domain.certification.dto.CertificationResponseDTO;
import com.assu.server.domain.certification.dto.CertificationSessionResponseDTO;
import com.assu.server.domain.certification.dto.GroupSessionRequest;
import com.assu.server.domain.certification.entity.AssociateCertification;
import com.assu.server.domain.certification.entity.enums.SessionJoinStatus;
import com.assu.server.domain.certification.entity.enums.SessionStatus;
import com.assu.server.domain.certification.repository.AssociateCertificationRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partnership.dto.PaperContentResponseDTO;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// AdminService 참조, 순환 참조 문제 주의
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class CertificationServiceImpl implements CertificationService {
	private final StoreRepository storeRepository;
	private final PaperContentRepository paperContentRepository;
	private final AssociateCertificationRepository associateCertificationRepository;
	private final MeterRegistry meterRegistry;
	// 세션 메니저
	private final CertificationSessionManager sessionManager;
	// AdminService 참조
	private final AdminService adminService;
	private final WebSocketBroadcaster webSocketBroadcaster;
	private final StudentRepository studentRepository;

	// 인증 직후 혜택 화면에서 사용 기록을 남기므로, 그보다 오래된 인증은 사용 근거로 인정하지 않는다
	private static final long USAGE_CLAIM_WINDOW_MINUTES = 30;



	@Override
	public CertificationResponseDTO getSessionId(
		CertificationGroupRequestDTO dto, Member member){
		Long userId = member.getId();

		if (dto.contentId() != null) {
			validateContentBelongsTo(dto.contentId(), dto.storeId(), dto.adminId());
		}

		Long sessionId = sessionManager.openSession(
			dto.storeId(), dto.people(), userId, dto.adminId(), dto.contentId());

		sessionManager.addUserToSession(sessionId, userId);

		return new CertificationResponseDTO(sessionId);

	}

	@Override
	public CertificationSessionResponseDTO getSession(Long sessionId) {
		if (!sessionManager.exists(sessionId)) {
			throw new GeneralException(ErrorStatus.NO_SUCH_SESSION);
		}

		Long storeId = Long.valueOf(sessionManager.getSessionInfo(sessionId, "storeId"));
		int peopleNumber = Integer.parseInt(sessionManager.getSessionInfo(sessionId, "peopleNumber"));
		String contentIdValue = sessionManager.getSessionInfo(sessionId, "contentId");

		Store store = storeRepository.findById(storeId).orElseThrow(
			() -> new GeneralException(ErrorStatus.NO_SUCH_STORE)
		);

		PaperContentResponseDTO partnershipContent = null;
		if (contentIdValue != null) {
			PaperContent content = paperContentRepository.findById(Long.valueOf(contentIdValue)).orElseThrow(
				() -> new GeneralException(ErrorStatus.NO_SUCH_CONTENT)
			);
			partnershipContent = PaperContentResponseDTO.toContentResponse(content);
		}

		return new CertificationSessionResponseDTO(
			sessionId,
			store.getId(),
			store.getName(),
			peopleNumber,
			sessionManager.snapshotUserIds(sessionId).size(),
			partnershipContent
		);
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
		if (!sessionManager.removeSession(sessionId)) {
			throw new GeneralException(ErrorStatus.NO_SUCH_SESSION);
		}

		webSocketBroadcaster.sendAfterCommit("/certification/progress/" + sessionId,
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
	@Transactional
	public CertificationProgressResponseDTO handleCertification(GroupSessionRequest dto, Member member) {
		Long userId = member.getId();
		Long sessionId = dto.sessionId();

		if (!sessionManager.exists(sessionId)) {
			countGroupFailure("no_such_session");
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
			webSocketBroadcaster.sendAfterCommit("/certification/progress/" + sessionId, response);
			countGroupFailure("mismatch");
			return response;
		}

		CertificationSessionManager.JoinResult joinResult = sessionManager.joinSession(sessionId, userId, targetPeople);

		if (joinResult.status() == SessionJoinStatus.DUPLICATE) {
			List<Long> currentCertifiedUserIds = sessionManager.snapshotUserIds(sessionId);
			CertificationProgressResponseDTO response = new CertificationProgressResponseDTO(
				"progress",
				null,
				"doubled member",
				currentCertifiedUserIds
			);
			// 곧 예외로 롤백되므로 커밋을 기다리지 않고 바로 알린다
			webSocketBroadcaster.send("/certification/progress/" + sessionId, response);
			countGroupFailure("doubled");
			throw new GeneralException(ErrorStatus.DOUBLE_CERTIFIED_USER);
		}
		if (joinResult.status() == SessionJoinStatus.NO_SESSION) {
			countGroupFailure("no_such_session");
			throw new GeneralException(ErrorStatus.NO_SUCH_SESSION);
		}
		if (joinResult.status() == SessionJoinStatus.FULL) {
			countGroupFailure("full");
			throw new GeneralException(ErrorStatus.SESSION_NOT_OPENED);
		}

		List<Long> currentCertifiedUserIds = joinResult.userIds();
		int currentCount = currentCertifiedUserIds.size();

		CertificationProgressResponseDTO response;
		if (currentCount >= targetPeople) {
			Store store = storeRepository.findById(storeId).orElseThrow(() -> {
				countGroupFailure("store_not_found");
				return new GeneralException(ErrorStatus.NO_SUCH_STORE);
			});

			// 세션 키를 삭제한 요청만 완료 처리한다. 만료 요청이 먼저 삭제했다면 완료로 기록하지 않는다.
			if (!sessionManager.removeSession(sessionId)) {
				countGroupFailure("no_such_session");
				throw new GeneralException(ErrorStatus.NO_SUCH_SESSION);
			}

			List<AssociateCertification> certifications = studentRepository.findAllById(currentCertifiedUserIds).stream()
				.map(participant -> AssociateCertification.builder()
					.store(store)
					.student(participant)
					.sessionId(sessionId)
					.peopleNumber(targetPeople)
					.isCertified(true)
					.status(SessionStatus.COMPLETED)
					.build())
				.toList();

			associateCertificationRepository.saveAll(certifications);

			response = new CertificationProgressResponseDTO("completed", currentCount, "인증 완료", currentCertifiedUserIds);
			webSocketBroadcaster.sendAfterCommit("/certification/progress/" + sessionId, response);
			countGroupSuccess("completed");
		} else {
			response = new CertificationProgressResponseDTO("progress", currentCount, null, currentCertifiedUserIds);
			webSocketBroadcaster.sendAfterCommit("/certification/progress/" + sessionId, response);
			countGroupSuccess("success");
		}
		return response;
	}

	@Override
	@Transactional
	public List<Long> claimCertifiedParticipants(Long requesterId, Long storeId, Long sessionId,
		List<Long> requestedUserIds) {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime since = now.minusMinutes(USAGE_CLAIM_WINDOW_MINUTES);

		AssociateCertification anchor;
		List<AssociateCertification> companions;
		if (sessionId != null) {
			List<AssociateCertification> sessionCertifications =
				associateCertificationRepository.findRecentBySessionIdAndStoreId(sessionId, storeId, since);
			anchor = sessionCertifications.stream()
				.filter(certification -> certification.getStudent().getId().equals(requesterId))
				.findFirst()
				.orElseThrow(() -> new GeneralException(ErrorStatus.USAGE_CERTIFICATION_REQUIRED));
			companions = sessionCertifications.stream()
				.filter(certification -> !certification.getId().equals(anchor.getId()))
				.toList();
		} else {
			List<AssociateCertification> requesterCertifications =
				associateCertificationRepository.findRecentByStudentIdAndStoreId(requesterId, storeId, since);
			if (requesterCertifications.isEmpty()) {
				throw new GeneralException(ErrorStatus.USAGE_CERTIFICATION_REQUIRED);
			}
			Optional<AssociateCertification> unused = requesterCertifications.stream()
				.filter(certification -> certification.getUsedAt() == null)
				.findFirst();
			if (unused.isEmpty()) {
				return List.of();
			}
			anchor = unused.get();
			companions = findRequestedCompanions(anchor, storeId, since, requestedUserIds);
		}

		if (anchor.getUsedAt() != null
			|| associateCertificationRepository.markUsedIfUnused(anchor.getId(), now) == 0) {
			return List.of();
		}

		List<Long> participantIds = new ArrayList<>();
		participantIds.add(requesterId);
		for (AssociateCertification companion : companions) {
			if (companion.getUsedAt() == null
				&& associateCertificationRepository.markUsedIfUnused(companion.getId(), now) == 1) {
				participantIds.add(companion.getStudent().getId());
			}
		}

		if (requestedUserIds != null && !participantIds.containsAll(requestedUserIds)) {
			log.warn("제휴 사용 기록 요청의 userIds 중 인증 근거가 없는 사용자를 제외 - requesterId: {}, storeId: {}, requested: {}, accepted: {}",
				requesterId, storeId, requestedUserIds, participantIds);
		}
		return participantIds;
	}

	private List<AssociateCertification> findRequestedCompanions(AssociateCertification anchor, Long storeId,
		LocalDateTime since, List<Long> requestedUserIds) {
		if (anchor.getSessionId() == null || requestedUserIds == null || requestedUserIds.isEmpty()) {
			return List.of();
		}
		Set<Long> requested = new HashSet<>(requestedUserIds);
		return associateCertificationRepository.findRecentBySessionIdAndStoreId(anchor.getSessionId(), storeId, since)
			.stream()
			.filter(certification -> !certification.getId().equals(anchor.getId()))
			.filter(certification -> requested.contains(certification.getStudent().getId()))
			.toList();
	}

	private void countGroupSuccess(String result){
		meterRegistry.counter("certification.group.result", "result", result, "reason", "none").increment();
	}

	private void countGroupFailure(String reason){
		meterRegistry.counter("certification.group.result", "result", "failure", "reason", reason).increment();
	}

	private void validateContentBelongsTo(Long contentId, Long storeId, Long adminId) {
		PaperContent content = paperContentRepository.findById(contentId).orElseThrow(
			() -> new GeneralException(ErrorStatus.NO_SUCH_CONTENT)
		);

		boolean isSameStore = content.getPaper().getStore().getId().equals(storeId);
		boolean isSameAdmin = content.getPaper().getAdmin().getId().equals(adminId);
		if (!isSameStore || !isSameAdmin) {
			throw new GeneralException(ErrorStatus.NO_SUCH_CONTENT);
		}
	}

	@Override
	@Transactional
	public void certificatePersonal(CertificationPersonalRequestDTO dto, Member member){
		// store id 추출
		Store store = storeRepository.findById(dto.storeId()).orElseThrow(
			() -> {
				meterRegistry.counter("certification.personal.result", "result", "failure", "reason", "store_not_found").increment();
				return new GeneralException(ErrorStatus.NO_SUCH_STORE);
			}
		);

		AssociateCertification personalCertificationData = dto.toPersonalCertification(store, member);
		associateCertificationRepository.save(personalCertificationData);
		meterRegistry.counter("certification.personal.result", "result", "success", "reason", "none").increment();
	}
}
