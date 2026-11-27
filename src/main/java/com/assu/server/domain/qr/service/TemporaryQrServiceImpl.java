package com.assu.server.domain.qr.service;

import static com.assu.server.domain.qr.dto.TemporaryQrRequestDTO.*;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.qr.dto.TemporaryQrRequestDTO;
import com.assu.server.domain.qr.dto.TemporaryQrResponseDTO;
import com.assu.server.domain.qr.entity.Qr;
import com.assu.server.domain.qr.repository.TemporaryQrRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class TemporaryQrServiceImpl implements TemporaryQrService{

	private final TemporaryQrRepository temporaryQrRepository;
	private final StudentRepository studentRepository;

	@Override
	@Transactional
	public void insertData(TemporaryQrRequestDTO dto, Member member){
		studentRepository.findById(member.getId()).orElseThrow(
			() -> new GeneralException(ErrorStatus.NO_SUCH_STUDENT)
		);

		Qr qr = toQr(dto,member.getId());
		temporaryQrRepository.save(qr);
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
