package com.assu.server.domain.user.dto;

import java.util.List;

import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;

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

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class UsablePartnershipDTO {
		private Long partnershipId;
		private String adminName;
		private String partnerName;
		private CriterionType criterionType;
		private OptionType optionType;
		private Integer people;
		private Long cost;
		private String note;
		private Long paperId;
		private String category;
		private Long discountRate;
	}

}
