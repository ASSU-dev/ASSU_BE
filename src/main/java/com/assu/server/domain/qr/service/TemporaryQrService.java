package com.assu.server.domain.qr.service;

import com.assu.server.domain.qr.dto.TemporaryQrRequestDTO;

public interface TemporaryQrService {

	void insertData(TemporaryQrRequestDTO dto);
	void increaseStamp(Long userId);

}
