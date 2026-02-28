package com.assu.server.domain.qr.service;

import static com.assu.server.domain.qr.dto.TemporaryQrRequestDTO.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.user.entity.StampEventApplicant;
import com.assu.server.domain.user.repository.StampEventApplicantRepository;
import org.springframework.stereotype.Service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.qr.dto.TemporaryQrRequestDTO;
import com.assu.server.domain.qr.dto.TemporaryQrResponseDTO;
import com.assu.server.domain.qr.entity.Qr;
import com.assu.server.domain.qr.repository.TemporaryQrRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.user.entity.Student;
import com.assu.server.domain.user.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Transactional
@Service
@RequiredArgsConstructor
public class TemporaryQrServiceImpl implements TemporaryQrService{

	private final TemporaryQrRepository temporaryQrRepository;
	private final StudentRepository studentRepository;
	private final StampEventApplicantRepository stampEventApplicantRepository;
	private final NotificationCommandService notificationCommandService;

	@Override
	public void insertData(TemporaryQrRequestDTO dto, Member member){
		Qr qr = toQr(dto,member.getId());
		increaseStamp(member.getId());
		temporaryQrRepository.save(qr);
	}



	private void increaseStamp(Long userId){
		Student student = studentRepository.findById(userId).orElseThrow(
			() -> new GeneralException(ErrorStatus.NO_SUCH_STUDENT)
		);
		student.setStamp();
		checkAndApplyStampEvent(student);
	}

	private void checkAndApplyStampEvent(Student student) {
		if (student.getStamp() % 10 == 0 && student.getStamp() > 0) {
			stampEventApplicantRepository.save(StampEventApplicant.builder()
					.student(student)
					.appliedAt(LocalDateTime.now())
					.eventVersion("2026_SEASON_1")
					.build());
			try {
				notificationCommandService.sendStamp(student.getId());
			} catch (Exception e) {
				// 알림 전송 실패해도 스탬프 적립은 성공
			}
			student.resetStamp();
		}
	}

	@Override
	public List<TemporaryQrResponseDTO> getTemporaryQrData(Member member) {
		Student student = studentRepository.findById(member.getId()).orElseThrow(
			() -> new GeneralException(ErrorStatus.NO_SUCH_STUDENT)
		);

		List<TemporaryQrResponseDTO> result = temporaryQrRepository.findByUserIdOrderByCreatedAtDesc(student.getId())
			.stream()
			.map(data -> new TemporaryQrResponseDTO(
				data.getAdminName(),
				data.getSort(),
				data.getCreatedAt().toString()
			))
			.collect(Collectors.toList());

		return result;
	}
}
