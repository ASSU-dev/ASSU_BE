package com.assu.server.domain.review.entity;

import com.assu.server.domain.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ReviewPhoto extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "review_id")
	private Review review;

	@Column(length =2000)
	private String photoUrl;

	@JoinColumn(name = "key_name") // S3 키 이름 저장 (조회 시 새 URL 생성용)
	private String keyName;

	public void updatePhotoUrl(String newPhotoUrl) {
		this.photoUrl = newPhotoUrl; //일시적 저장
	}

}