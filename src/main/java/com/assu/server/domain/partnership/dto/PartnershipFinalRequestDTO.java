package com.assu.server.domain.partnership.dto;

import java.time.LocalDate;
import java.util.List;

import com.assu.server.domain.student.entity.PartnershipUsage;
import com.assu.server.domain.student.entity.Student;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PartnershipFinalRequestDTO(

	@NotNull(message="storeId를 입력해주세요")
	@Schema(description="storeId 입력")
	Long storeId,

	@Schema(description = "adminId 입력")
	@NotNull(message="adminId를 입력해주세요")
	Long adminId,

	@Schema(description = "00~99 사이의 tableNumber를 입력해주세요")
	@NotNull(message = "00~99 사이의 tableNumber를 입력해주세요")
	String tableNumber,
	@Schema(description = "@@학생회 이름 그대로 입력해주세요")
	@NotBlank(message = "학생회 이름을 '~~학생회' 와 같이 입력해주세요")
	String adminName,
	@Schema(description = "가게 명을 입력해주세요")
	@NotBlank(message="가게 명을 입력해주세요")
	String placeName,
	@Schema(description = "제휴 항목을 그대로 입력해주세요")
	@NotBlank(message = "받은 제휴 항목을 그대로 입력해주세요")
	String partnershipContent,
	@Schema(description = "제휴 콘텐츠 아이디를 입력해주세요")
	@NotNull(message="제휴 콘텐츠 아이디를 입력해주세요")
	Long contentId,

	Long discount, // 이거 사실 필요 없는건데 나중에 db 옮길때 한꺼번에 처리하려구요
	@Schema(description = "함께 인증한 유저들의 아이디를 입력해주세요(없을 시 공란). 서버의 인증 기록과 일치하는 유저만 기록됩니다.", example = "[1, 2]")
	List<Long>userIds,
	@Schema(description = "그룹 인증 세션 ID (선택). 전달하면 해당 세션의 인증 완료자 전원을 서버 기록 기준으로 기록하고 userIds는 무시합니다.", example = "123")
	Long sessionId
) {
	public PartnershipUsage toPartnershipUsage(Student student, Long paperId) {
		return PartnershipUsage.builder()
			.adminName(this.adminName())
			.date(LocalDate.now())
			.place(this.placeName())
			.student(student)
			.paperId(paperId)
			.isReviewed(false)
			.contentId(this.contentId())
			.partnershipContent(this.partnershipContent())
			.build();
	}
}
