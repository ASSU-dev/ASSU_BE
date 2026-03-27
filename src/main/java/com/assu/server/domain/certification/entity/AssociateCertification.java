package com.assu.server.domain.certification.entity;
import com.assu.server.domain.certification.entity.enums.SessionStatus;
import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.student.entity.Student;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class AssociateCertification extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Integer tableNumber;
	private Boolean isCertified;
	private Integer peopleNumber;

	@Enumerated(EnumType.STRING)
	private SessionStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "store_id")
	private Store store;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "partner_id")
	private Partner partner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "student_id")
	private Student student;

	public void setStatus(SessionStatus status) {
		this.status = status;
	}
	public void setIsCertified(Boolean isCertified) {
		this.isCertified = isCertified;
	}


}