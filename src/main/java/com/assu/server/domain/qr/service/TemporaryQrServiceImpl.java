package com.assu.server.domain.qr.service;

import org.springframework.stereotype.Service;

import com.assu.server.domain.qr.dto.TemporaryQrRequestDTO;
import com.assu.server.domain.qr.entity.Qr;
import com.assu.server.domain.qr.repository.TemporaryQrRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.user.entity.Student;
import com.assu.server.domain.user.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Transactional
@Service
@RequiredArgsConstructor
public class TemporaryQrServiceImpl implements TemporaryQrService{

	private TemporaryQrRepository temporaryQrRepository;
	private StoreRepository storeRepository;
	private StudentRepository studentRepository;

	@Override
	public void insertData(TemporaryQrRequestDTO dto){
		Store store = storeRepository.findById(dto.storeId()).orElseThrow(
			() -> new GeneralException(ErrorStatus.NO_SUCH_STORE)
		);
		Student student = studentRepository.findById(dto.userId()).orElseThrow(
			() -> new GeneralException(ErrorStatus.NO_SUCH_STUDENT)
		);

		Qr qr = dto.toQr();
		temporaryQrRepository.save(qr);
	}


	@Override
	public void increaseStamp(Long userId){
		Student student = studentRepository.findById(userId).orElseThrow(
			() -> new GeneralException(ErrorStatus.NO_SUCH_STUDENT)
		);
		student.setStamp();
	}
}
