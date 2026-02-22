package com.assu.server.domain.partnership.entity;
import java.time.LocalDate;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.store.entity.Store;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Paper extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Setter
	private LocalDate partnershipPeriodStart;

	@Setter
	private LocalDate partnershipPeriodEnd;

	@Setter
    @Enumerated(EnumType.STRING)
	private ActivationStatus isActivated;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "admin_id")
	private Admin admin;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "partner_id")
	private Partner partner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "store_id")
	private Store store;

	@Column(name = "contract_image_key", length = 512)
	private String contractImageKey;

	public void updateContractImageKey(String key) { this.contractImageKey = key; }
}
