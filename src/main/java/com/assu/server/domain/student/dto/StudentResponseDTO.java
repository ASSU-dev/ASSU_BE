package com.assu.server.domain.student.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.assu.server.domain.student.entity.StampEventApplicant;
import com.assu.server.domain.student.entity.Student;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

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

    @Builder
    public record CheckStampResponseDTO(
        Long userId,
        int stamp,
        String message
    ) {
        public static CheckStampResponseDTO from(Student student, String message) {
            return new CheckStampResponseDTO(student.getId(), student.getStamp(), message);
        }
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

	@Builder
	public record UsablePartnershipDTO(
		Long partnershipId,
		String adminName,
		String partnerName,
		Long paperId,
		Long storeId,
		Integer extraCount,
		String partnerProfileUrl,
		@Schema(description = "혜택 조건", example = "30,000원 이상 주문 시") String benefitCondition,
		@Schema(description = "혜택 내용", example = "음료 1캔 제공") String benefit,
		@Schema(description = "유의사항", example = "학생증 지참") String caution,
		@Schema(description = "그룹 인증 최소 인원", example = "4") Integer minPeople
	) {}

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
