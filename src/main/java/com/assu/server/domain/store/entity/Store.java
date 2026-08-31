package com.assu.server.domain.store.entity;
import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.store.entity.enums.StoreCategory;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;


@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Store extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "partner_id")
	private Partner partner;

	@Setter
	private Integer rate;

	@Setter
	@Enumerated(EnumType.STRING)
	private ActivationStatus isActivate;

	@Setter
	private String name;

	private String address;

	private String detailAddress;

	@JdbcTypeCode(SqlTypes.GEOMETRY)
	private Point point;

	private double latitude;
	private double longitude;

	@Enumerated(EnumType.STRING)
	private StoreCategory storeCategory;

	public void linkPartner(Partner partner) {
		this.partner = partner;
	}
	public void setGeo(Double lat, Double lng, Point point) {
		this.latitude = lat;
		this.longitude = lng;
		this.point = point;
	}

}