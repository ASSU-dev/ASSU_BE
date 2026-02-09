package com.assu.server.domain.certification.service;

import com.assu.server.domain.certification.dto.CertificationGroupRequestDTO;
import com.assu.server.domain.certification.dto.CertificationPersonalRequestDTO;
import com.assu.server.domain.certification.dto.CertificationResponseDTO;
import com.assu.server.domain.certification.dto.GroupSessionRequest;
import com.assu.server.domain.member.entity.Member;

public interface CertificationService {

	CertificationResponseDTO getSessionId(CertificationGroupRequestDTO dto, Member member);

	void handleCertification(GroupSessionRequest dto, Member member);

	void certificatePersonal(CertificationPersonalRequestDTO dto, Member member);
}
