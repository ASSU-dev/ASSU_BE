package com.assu.server.domain.review.entity;
import java.util.ArrayList;
import java.util.List;

import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.common.entity.enums.ReportedStatus;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.user.entity.Student;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Review extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "student_id")
	private Student student;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "partner_id")
	private Partner partner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "store_id")
	private Store store;

	@OneToMany(mappedBy = "review", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<ReviewPhoto> imageList = new ArrayList<>();

	private Integer rate;
	private String content;

	private String affiliation;

	@Enumerated(EnumType.STRING)
	@Builder.Default
	private ReportedStatus status = ReportedStatus.NORMAL;

	public void updateReportedStatus(ReportedStatus status) {
		this.status = status;
	}
}
