package com.assu.server.domain.qr.service;

import java.util.List;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.qr.dto.TemporaryQrRequestDTO;
import com.assu.server.domain.qr.dto.TemporaryQrResponseDTO;

public interface TemporaryQrService {

	void insertData(TemporaryQrRequestDTO dto, Member member);
	List<TemporaryQrResponseDTO> getTemporaryQrData(Member member);

}
