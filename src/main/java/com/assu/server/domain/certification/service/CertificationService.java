package com.assu.server.domain.certification.service;

import com.assu.server.domain.certification.dto.CertificationGroupRequestDTO;
import com.assu.server.domain.certification.dto.CertificationPersonalRequestDTO;
import com.assu.server.domain.certification.dto.CertificationProgressResponseDTO;
import com.assu.server.domain.certification.dto.CertificationResponseDTO;
import com.assu.server.domain.certification.dto.CertificationSessionResponseDTO;
import com.assu.server.domain.certification.dto.GroupSessionRequestDTO;
import com.assu.server.domain.member.entity.Member;

public interface CertificationService {

	CertificationResponseDTO getSessionId(CertificationGroupRequestDTO dto, Member member);

	CertificationSessionResponseDTO getSession(Long sessionId);

	void expireSession(Long sessionId, Member member);

	CertificationProgressResponseDTO handleCertification(GroupSessionRequestDTO dto, Member member);

	void certificatePersonal(CertificationPersonalRequestDTO dto, Member member);
}
