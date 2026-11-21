package com.assu.server.domain.partnership.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.assu.server.domain.partnership.dto.PartnershipOptionRequestDTO;

class PaperContentTest {

	@Test
	void toDisplayText_whenAllFieldsExist_thenJoinsConditionBenefitAndCaution() {
		PaperContent content = PaperContent.builder()
			.benefitCondition("30,000원 이상 주문 시")
			.benefit("음료 1캔 제공")
			.caution("학생증 지참")
			.build();

		assertThat(content.toDisplayText()).isEqualTo("30,000원 이상 주문 시 음료 1캔 제공 (학생증 지참)");
	}

	@Test
	void toDisplayText_whenOnlyBenefit_thenReturnsBenefit() {
		PaperContent content = PaperContent.builder()
			.benefit("모든 시술 10% 할인")
			.build();

		assertThat(content.toDisplayText()).isEqualTo("모든 시술 10% 할인");
	}

	@Test
	void toPaperContent_whenRequestGiven_thenStoresNewFields() {
		PartnershipOptionRequestDTO request = new PartnershipOptionRequestDTO(
			"2인 이상 방문 시", "음료 1캔 제공", null, 2
		);

		PaperContent content = request.toPaperContent(null);

		assertThat(content.getBenefitCondition()).isEqualTo("2인 이상 방문 시");
		assertThat(content.getBenefit()).isEqualTo("음료 1캔 제공");
		assertThat(content.getCaution()).isNull();
		assertThat(content.getMinPeople()).isEqualTo(2);
	}
}
