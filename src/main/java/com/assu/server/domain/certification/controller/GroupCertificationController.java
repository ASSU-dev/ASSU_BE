package com.assu.server.domain.certification.controller;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.assu.server.domain.certification.dto.GroupSessionRequest;
import com.assu.server.domain.certification.service.CertificationService;
import com.assu.server.global.util.PrincipalDetails;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller // STOMP 메시지 처리를 위한 컨트롤러
@RequiredArgsConstructor
@Component
@RequestMapping("/app")
public class GroupCertificationController {

	private final CertificationService certificationService;

	@MessageMapping("/certify")
	@Operation(
		summary = "그룹 인증 요청 WebSocket API",
		description = "# [v1.0 (2025-12-23)](https://clumsy-seeder-416.notion.site/x-22b1197c19ed801d99b1ddb7c5d7ee26?source=copy_link)\n" +
			"- WebSocket을 통해 그룹 인증 요청을 보냅니다.\n" +
			"- 로그인 필요\n" +
			"- Payload는 JSON 형식입니다.\n\n" +
			"**Request Payload:**\n" +
			"  - `adminId` (Long, required): 인증하고자 하는 제휴의 관리자 ID\n" +
			"  - `sessionId` (Long, required): 인증하고자 하는 그룹 세션 ID\n\n"
	)
	public void certifyGroup(@Payload GroupSessionRequest dto,
		Principal principal) {
		if (principal instanceof UsernamePasswordAuthenticationToken) {
			UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken)principal;
			PrincipalDetails principalDetails = (PrincipalDetails)auth.getPrincipal();

			try {
				log.info("### SUCCESS ### 인증 요청 메시지 수신 - user: {}, adminId: {}, sessionId: {}",
					principalDetails.getUsername(), dto.adminId(), dto.sessionId());

				if (principalDetails != null) {
					certificationService.handleCertification(dto, principalDetails.getMember());
				}
			} catch (Exception e) {
			}
		}
	}

}
