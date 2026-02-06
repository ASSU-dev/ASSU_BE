package com.assu.server.domain.certification.dto;

import com.assu.server.domain.certification.entity.AssociateCertification;
import com.assu.server.domain.certification.entity.enums.SessionStatus;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.store.entity.Store;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CertificationGroupRequestDTO(
	@Schema(description = "1명 이상의 사람 수를 입력해주세요")
	@NotNull(message= "1명 이상의 사람 수를 입력해주세요")
	Integer people,
	@Schema(description = "storeId를 입력해주세요")
	@NotNull(message = "storeId를 입력해주세요")
	Long storeId,
	@Schema(description = "adminId를 입력해주세요")
	@NotNull(message="adminId를 입력해주세요")
	Long adminId,
	@Schema(description = "00~99 사이의 tableNumber를 입력해주세요")
	@NotNull(message = "00~99 사이의 tableNumber를 입력해주세요")
	Integer tableNumber
) {
	public AssociateCertification toAssociateCertification(Store store, Member member) {
		return AssociateCertification.builder()
			.store(store)
			.partner(store.getPartner())
			.status(SessionStatus.OPENED)
			.isCertified(false)
			.peopleNumber(this.people())
			.tableNumber(this.tableNumber())
			.student(member.getStudentProfile())
			.build();
	}
}
