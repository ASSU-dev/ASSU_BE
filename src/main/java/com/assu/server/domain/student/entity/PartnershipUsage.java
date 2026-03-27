package com.assu.server.domain.student.entity;
import java.time.LocalDate;

import com.assu.server.domain.common.entity.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PartnershipUsage extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "student_id")
	private Student student;

	private String place;
	private LocalDate date;
	private String partnershipContent;
	private Boolean isReviewed;
	private Integer discount;
	private Long paperId;
	private Long contentId;

	private String adminName;

	public void setIsReviewed(Boolean isReviewed) {
		this.isReviewed = isReviewed;
	}

}