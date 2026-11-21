package com.assu.server.domain.partnership.entity;

import com.assu.server.domain.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PaperContent extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "paper_id")
	private Paper paper;

	@Column(name = "benefit_condition", length = 255)
	private String benefitCondition;

	@Column(name = "benefit", columnDefinition = "TEXT")
	private String benefit;

	@Column(name = "caution", length = 255)
	private String caution;

	@Column(name = "min_people")
	private Integer minPeople;

	// goods 테이블 삭제 전까지 FK 정리를 위해 매핑만 유지한다
	@BatchSize(size = 100)
	@OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Goods> goods = new ArrayList<>();

	public String toDisplayText() {
		String text = benefitCondition == null || benefitCondition.isBlank()
			? benefit
			: benefitCondition + " " + benefit;
		return caution == null || caution.isBlank() ? text : text + " (" + caution + ")";
	}
}
