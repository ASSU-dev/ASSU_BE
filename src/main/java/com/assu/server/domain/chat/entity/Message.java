package com.assu.server.domain.chat.entity;

import com.assu.server.domain.chat.dto.ChatRequestDTO;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.chat.entity.enums.MessageType;

import com.assu.server.domain.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Message extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "room_id")
	private ChattingRoom chattingRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = true) // 그룹 채팅이면 nullable
    private Member receiver;


    @Enumerated(EnumType.STRING)
	private MessageType type;

	private String message;

    private Integer unreadCount;

//	private LocalDateTime sendTime;
//	private LocalDateTime readTime;

    @Column(nullable = false)
	private boolean isRead = false;

    @Builder.Default
    private Boolean deleted = false;

    public void markAsRead() {
        this.isRead = true;
        this.unreadCount = 0;
    }

    public static Message toMessageEntity(
            ChatRequestDTO.ChatMessageRequestDTO request,
            ChattingRoom room,
            Member sender,
            Member receiver,
            int unreadForSender
    ) {
        return Message.builder()
                .chattingRoom(room)
                .sender(sender)
                .receiver(receiver)
                .message(request.message())
                .unreadCount(unreadForSender)
                .type(MessageType.TEXT)
                .build();
    }

    public static Message toGuideMessageEntity(
            ChatRequestDTO.ChatMessageRequestDTO request,
            ChattingRoom room,
            Member sender,
            Member receiver
    ) {
        return Message.builder()
                .chattingRoom(room)
                .sender(sender)
                .receiver(receiver)
                .message(request.message())
                .unreadCount(0)
                .type(MessageType.GUIDE)
                .build();
    }
}