package com.assu.server.domain.notification.entity;
import com.assu.server.domain.common.entity.BaseEntity;

import com.assu.server.domain.member.entity.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "receiver_id", nullable = false)
	private Member receiver;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private NotificationType type;

	@NotNull
	@Column(nullable = false)
	private Long refId;

	@NotNull
	@Column(nullable = false)
	private String title;

	@NotNull
	@Column(nullable = false, columnDefinition = "TEXT")
	private String messagePreview;

	@NotNull
	private String deeplink; // ex) /chat/rooms/123

	@NotNull
	@Column(nullable = false)
	private boolean isRead = false;

	@Column(nullable = true)
	private LocalDateTime readAt;

	public void markRead() {
		markReadAt(LocalDateTime.now());
	}

	public void markReadAt(LocalDateTime readTime) {
		if (!this.isRead) {
			this.isRead = true;
			this.readAt = readTime;
		}
	}
}