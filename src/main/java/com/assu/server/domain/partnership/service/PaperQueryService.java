package com.assu.server.domain.partnership.service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partnership.dto.PaperResponseDTO;

public interface PaperQueryService {
	PaperResponseDTO getStorePaperContent(Long storeId, Member member);
}