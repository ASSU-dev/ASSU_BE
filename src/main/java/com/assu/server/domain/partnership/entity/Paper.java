package com.assu.server.domain.partnership.entity;
import java.time.LocalDate;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;

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

	@Enumerated(EnumType.STRING)
	@Column(name = "status_before_partner_withdrawal")
	private ActivationStatus statusBeforePartnerWithdrawal;

	public void updateContractImageKey(String key) { this.contractImageKey = key; }

	// 탈퇴 업체 제휴는 종료만 허용하고, 수동 종료가 복구보다 우선하도록 기록을 지운다
	public void changeStatus(ActivationStatus nextStatus) {
		// 탈퇴 전 상태 기록이 없는 경우(탈퇴 당시 이미 종료, 탈퇴 기간 중 종료)도 막도록 업체 탈퇴 여부를 직접 본다
		if (nextStatus != ActivationStatus.INACTIVE && isOwnedByWithdrawnPartner()) {
			throw new DatabaseException(ErrorStatus.WITHDRAWN_PARTNER_PAPER);
		}
		this.isActivated = nextStatus;
		this.statusBeforePartnerWithdrawal = null;
	}

	private boolean isOwnedByWithdrawnPartner() {
		return isWithdrawn(partner) || (store != null && isWithdrawn(store.getPartner()));
	}

	private static boolean isWithdrawn(Partner owner) {
		return owner != null && owner.getMember() != null && owner.getMember().isWithdrawn();
	}
}
