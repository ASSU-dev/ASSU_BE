package com.assu.server.domain.qr.entity;

import com.assu.server.domain.common.entity.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Qr extends BaseEntity {
	@Id
	private Long id;

	private Long storeId;

	private Long userId;
}
