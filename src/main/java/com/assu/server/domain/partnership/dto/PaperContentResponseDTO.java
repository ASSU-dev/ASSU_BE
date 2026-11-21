package com.assu.server.domain.partnership.dto;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaperContentResponseDTO(
	@Schema(description = "학생회 ID", example = "1")
	Long adminId,

	@Schema(description = "학생회 이름", example = "IT대학 학생회")
	String adminName,

	@Schema(description = "화면 표시용 전체 문구 (조건 + 혜택)", example = "30,000원 이상 주문 시 음료 1캔 제공")
	String paperContent,

	@Schema(description = "제휴 내용 ID", example = "3")
	Long contentId,

	@Schema(description = "제휴 기간", example = "제휴기간 2026.03.01 ~ 2026.12.31")
	String partnershipPeriod,

	@Schema(description = "혜택 조건", example = "30,000원 이상 주문 시")
	String benefitCondition,

	@Schema(description = "혜택 내용", example = "음료 1캔 제공")
	String benefit,

	@Schema(description = "유의사항", example = "학생증 지참")
	String caution,

	@Schema(description = "그룹 인증 최소 인원", example = "4")
	Integer minPeople
) {
	private static final DateTimeFormatter PERIOD_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

	public static List<PaperContentResponseDTO> toContentResponseList(List<PaperContent> contents) {
		return contents.stream()
			.map(PaperContentResponseDTO::toContentResponse)
			.toList();
	}

	public static PaperContentResponseDTO toContentResponse(PaperContent content) {
		return new PaperContentResponseDTO(
			content.getPaper().getAdmin().getId(),
			content.getPaper().getAdmin().getName(),
			content.toDisplayText(),
			content.getId(),
			buildPartnershipPeriod(content.getPaper()),
			content.getBenefitCondition(),
			content.getBenefit(),
			content.getCaution(),
			content.getMinPeople()
		);
	}

	private static String buildPartnershipPeriod(Paper paper) {
		if (paper.getPartnershipPeriodStart() == null || paper.getPartnershipPeriodEnd() == null) {
			return null;
		}
		return "제휴기간 " + paper.getPartnershipPeriodStart().format(PERIOD_DATE_FORMAT)
			+ " ~ " + paper.getPartnershipPeriodEnd().format(PERIOD_DATE_FORMAT);
	}
}
