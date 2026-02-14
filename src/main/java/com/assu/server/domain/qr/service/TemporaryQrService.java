package com.assu.server.domain.qr.service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.qr.dto.TemporaryQrRequestDTO;

public interface TemporaryQrService {

	void insertData(TemporaryQrRequestDTO dto, Member member);
	void increaseStamp(Long userId);

}
