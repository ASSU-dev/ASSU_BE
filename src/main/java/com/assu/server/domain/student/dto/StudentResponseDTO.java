package com.assu.server.domain.student.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.assu.server.domain.student.entity.StampEventApplicant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class StudentResponseDTO {

	public record MyPartnership (
		long serviceCount,
		List<UsageDetail> details
	){}


	public record UsageDetail (
		String adminName,
		Long partnershipUsageId,
		String storeName,
		Long partnerId,
		Long storeId,
		String usedAt,
		String benefitDescription,
		boolean isReviewed
	){}

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CheckStampResponseDTO {
        private Long userId;
        private int stamp;
        private String message;
    }

	public record RecommendCarouselDTO(
		String partnerProfileUrl,
		String partnerName,
		Long storeId,
		@Schema(description = "대표 혜택 조건", example = "30,000원 이상 주문 시")
		String benefitCondition,
		@Schema(description = "대표 혜택 내용", example = "음료 1캔 제공")
		String benefit
	) {}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class UsablePartnershipDTO {
		private Long partnershipId;
		private String adminName;
		private String partnerName;
		private Long paperId;
		private Long storeId;
		private Integer extraCount;
		private String partnerProfileUrl;
		@Schema(description = "혜택 조건", example = "30,000원 이상 주문 시")
		private String benefitCondition;
		@Schema(description = "혜택 내용", example = "음료 1캔 제공")
		private String benefit;
		@Schema(description = "유의사항", example = "학생증 지참")
		private String caution;
		@Schema(description = "그룹 인증 최소 인원", example = "4")
		private Integer minPeople;
	}

	@Schema(description = "스탬프 이벤트 응모자")
	public record StampEventApplicantDTO(
		@Schema(description = "학생 ID(회원 ID)", example = "1") Long studentId,
		@Schema(description = "학생 이름", example = "홍길동") String name,
		@Schema(description = "응모 회차", example = "2026_SEASON_1") String eventVersion,
		@Schema(description = "응모 일시", example = "2026-11-21T10:00:00") LocalDateTime appliedAt
	) {
		public static StampEventApplicantDTO from(StampEventApplicant applicant) {
			return new StampEventApplicantDTO(
				applicant.getStudent().getId(),
				applicant.getStudent().getName(),
				applicant.getEventVersion(),
				applicant.getAppliedAt()
			);
		}
	}

}
