package com.assu.server.domain.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.chat.dto.ChatRequestDTO;
import com.assu.server.domain.chat.dto.ChatResponseDTO;
import com.assu.server.domain.chat.dto.MessageHandlingResult;
import com.assu.server.domain.chat.entity.ChattingRoom;
import com.assu.server.domain.chat.entity.Message;
import com.assu.server.domain.chat.entity.enums.MessageType;
import com.assu.server.domain.chat.repository.ChatRepository;
import com.assu.server.domain.chat.repository.MessageRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.util.PresenceTracker;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

	@InjectMocks
	private ChatServiceImpl chatService;

	@Mock
	private ChatRepository chatRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private PartnerRepository partnerRepository;

	@Mock
	private AdminRepository adminRepository;

	@Mock
	private MessageRepository messageRepository;

	@Mock
	private StoreRepository storeRepository;

	@Mock
	private SimpMessagingTemplate simpMessagingTemplate;

	@Mock
	private NotificationCommandService notificationCommandService;

	@Mock
	private PresenceTracker presenceTracker;

	private static final Long ADMIN_ID = 10L;
	private static final Long PARTNER_ID = 20L;
	private static final Long ROOM_ID = 1L;

	// ===== createChatRoom =====

	private Admin givenAdmin(Long memberId) {
		Member adminMember = mock(Member.class);
		lenient().when(adminMember.getId()).thenReturn(memberId);
		return Admin.builder().id(memberId).member(adminMember).name("총학생회").isPhoneVerified(false).build();
	}

	private Partner givenPartner(Long memberId) {
		Member partnerMember = mock(Member.class);
		lenient().when(partnerMember.getId()).thenReturn(memberId);
		return Partner.builder().id(memberId).member(partnerMember).name("역전할머니 맥주").isPhoneVerified(false).build();
	}

	@Test
	@DisplayName("존재하지 않는 관리자와 채팅방을 생성하려고 하면 NO_SUCH_ADMIN 예외가 발생한다")
	void createChatRoom_AdminNotFound_ThrowsException() {
		// 1. Given
		ChatRequestDTO.CreateChatRoomRequestDTO request =
			new ChatRequestDTO.CreateChatRoomRequestDTO(ADMIN_ID, PARTNER_ID);
		when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> chatService.createChatRoom(request, PARTNER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_ADMIN, exception.getCode());
		verify(chatRepository, never()).save(any());
	}

	@Test
	@DisplayName("파트너의 매장이 아닌 매장이 조회되면 NO_SUCH_STORE_WITH_THAT_PARTNER 예외가 발생한다")
	void createChatRoom_StorePartnerMismatch_ThrowsException() {
		// 1. Given (조회된 매장이 다른 파트너 소유)
		ChatRequestDTO.CreateChatRoomRequestDTO request =
			new ChatRequestDTO.CreateChatRoomRequestDTO(ADMIN_ID, PARTNER_ID);

		Admin admin = givenAdmin(ADMIN_ID);
		Partner partner = givenPartner(PARTNER_ID);
		Partner otherPartner = givenPartner(99L);
		Store store = Store.builder().id(500L).partner(otherPartner).build();

		when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
		when(storeRepository.findByPartnerId(PARTNER_ID)).thenReturn(Optional.of(store));

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> chatService.createChatRoom(request, PARTNER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_STORE_WITH_THAT_PARTNER, exception.getCode());
		verify(chatRepository, never()).save(any());
	}

	@Test
	@DisplayName("이미 두 사람의 채팅방이 있으면 기존 채팅방을 isNew=false로 반환한다")
	void createChatRoom_ExistingRoom_ReturnsExisting() {
		// 1. Given
		ChatRequestDTO.CreateChatRoomRequestDTO request =
			new ChatRequestDTO.CreateChatRoomRequestDTO(ADMIN_ID, PARTNER_ID);

		Admin admin = givenAdmin(ADMIN_ID);
		Partner partner = givenPartner(PARTNER_ID);
		Store store = Store.builder().id(500L).partner(partner).build();
		ChattingRoom existingRoom = ChattingRoom.builder()
			.id(ROOM_ID).admin(admin).partner(partner).build();

		when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
		when(storeRepository.findByPartnerId(PARTNER_ID)).thenReturn(Optional.of(store));
		when(chatRepository.findChattingRoomByAdminIdAndPartnerId(ADMIN_ID, PARTNER_ID)).thenReturn(existingRoom);

		// 2. When
		ChatResponseDTO.CreateChatRoomResponseDTO response = chatService.createChatRoom(request, PARTNER_ID);

		// 3. Then
		assertEquals(ROOM_ID, response.roomId());
		assertFalse(response.isNew());
		verify(chatRepository, never()).save(any());
	}

	@Test
	@DisplayName("채팅방이 없으면 ACTIVE 상태·인원 2명의 새 채팅방을 생성하고 isNew=true로 반환한다")
	void createChatRoom_NewRoom_SavesAndReturns() {
		// 1. Given
		ChatRequestDTO.CreateChatRoomRequestDTO request =
			new ChatRequestDTO.CreateChatRoomRequestDTO(ADMIN_ID, PARTNER_ID);

		Admin admin = givenAdmin(ADMIN_ID);
		Partner partner = givenPartner(PARTNER_ID);
		Store store = Store.builder().id(500L).partner(partner).build();

		when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
		when(storeRepository.findByPartnerId(PARTNER_ID)).thenReturn(Optional.of(store));
		when(chatRepository.findChattingRoomByAdminIdAndPartnerId(ADMIN_ID, PARTNER_ID)).thenReturn(null);

		ChattingRoom savedRoom = ChattingRoom.builder()
			.id(ROOM_ID).admin(admin).partner(partner).build();
		when(chatRepository.save(any(ChattingRoom.class))).thenReturn(savedRoom);

		// 2. When
		ChatResponseDTO.CreateChatRoomResponseDTO response = chatService.createChatRoom(request, PARTNER_ID);

		// 3. Then
		assertEquals(ROOM_ID, response.roomId());
		assertTrue(response.isNew());

		// 저장된 방의 상태 검증
		ArgumentCaptor<ChattingRoom> captor = ArgumentCaptor.forClass(ChattingRoom.class);
		verify(chatRepository).save(captor.capture());
		ChattingRoom toSave = captor.getValue();
		assertEquals(ActivationStatus.ACTIVE, toSave.getStatus());
		assertEquals(2, toSave.getMemberCount());
		assertEquals("역전할머니 맥주", toSave.getAdminViewName());
		assertEquals("총학생회", toSave.getPartnerViewName());
	}

	// ===== handleMessage =====

	private Member givenMember(Long id) {
		Member member = mock(Member.class);
		lenient().when(member.getId()).thenReturn(id);
		when(memberRepository.findById(id)).thenReturn(Optional.of(member));
		return member;
	}

	@Test
	@DisplayName("존재하지 않는 채팅방으로 메시지를 보내면 NO_SUCH_ROOM 예외가 발생한다")
	void handleMessage_RoomNotFound_ThrowsException() {
		// 1. Given
		ChatRequestDTO.ChatMessageRequestDTO request =
			new ChatRequestDTO.ChatMessageRequestDTO(ROOM_ID, PARTNER_ID, ADMIN_ID, "안녕하세요");
		when(chatRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> chatService.handleMessage(request));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_ROOM, exception.getCode());
		verify(messageRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("수신자가 채팅방에 접속 중이면 읽음 상태로 저장되고 알림 없이 결과가 반환된다")
	void handleMessage_ReceiverInRoom_NoNotification() {
		// 1. Given
		ChatRequestDTO.ChatMessageRequestDTO request =
			new ChatRequestDTO.ChatMessageRequestDTO(ROOM_ID, PARTNER_ID, ADMIN_ID, "안녕하세요");

		ChattingRoom room = ChattingRoom.builder().id(ROOM_ID).build();
		when(chatRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
		Member sender = givenMember(PARTNER_ID);
		Member receiver = givenMember(ADMIN_ID);
		when(presenceTracker.isInRoom(ADMIN_ID, ROOM_ID)).thenReturn(true);

		Message saved = Message.builder()
			.id(100L).chattingRoom(room).sender(sender).receiver(receiver)
			.message("안녕하세요").unreadCount(0).isRead(true).type(MessageType.TEXT)
			.build();
		when(messageRepository.saveAndFlush(any(Message.class))).thenReturn(saved);

		// 2. When
		MessageHandlingResult result = chatService.handleMessage(request);

		// 3. Then
		assertFalse(result.hasRoomUpdates());
		assertEquals(0, result.sendMessageResponseDTO().unreadCountForSender());
		verify(notificationCommandService, never()).sendChat(any(), any(), any(), any());

		// 저장된 메시지가 읽음 상태(unread 0)로 생성되었는지 검증
		ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
		verify(messageRepository).saveAndFlush(captor.capture());
		assertEquals(0, captor.getValue().getUnreadCount());
		assertTrue(captor.getValue().isRead());
	}

	@Test
	@DisplayName("수신자가 부재중이면 안읽음 메시지로 저장되고 채팅방 업데이트와 푸시 알림이 발생한다")
	void handleMessage_ReceiverAbsent_SendsNotificationAndUpdates() {
		// 1. Given
		ChatRequestDTO.ChatMessageRequestDTO request =
			new ChatRequestDTO.ChatMessageRequestDTO(ROOM_ID, PARTNER_ID, ADMIN_ID, "안녕하세요");

		ChattingRoom room = ChattingRoom.builder().id(ROOM_ID).build();
		when(chatRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

		Member sender = givenMember(PARTNER_ID);
		when(sender.getRole()).thenReturn(UserRole.PARTNER);
		when(sender.getPartnerProfile()).thenReturn(
			Partner.builder().name("역전할머니 맥주").isPhoneVerified(false).build());

		Member receiver = givenMember(ADMIN_ID);
		when(presenceTracker.isInRoom(ADMIN_ID, ROOM_ID)).thenReturn(false);

		Message saved = Message.builder()
			.id(100L).chattingRoom(room).sender(sender).receiver(receiver)
			.message("안녕하세요").unreadCount(1).isRead(false).type(MessageType.TEXT)
			.build();
		when(messageRepository.saveAndFlush(any(Message.class))).thenReturn(saved);
		when(messageRepository.countUnreadMessagesByRoomAndReceiver(ROOM_ID, ADMIN_ID)).thenReturn(3L);

		// 2. When
		MessageHandlingResult result = chatService.handleMessage(request);

		// 3. Then
		assertTrue(result.hasRoomUpdates());
		assertEquals(ADMIN_ID, result.receiverId());
		assertEquals(3L, result.chatRoomUpdateDTO().unreadCount());
		assertEquals("안녕하세요", result.chatRoomUpdateDTO().lastMessage());

		verify(notificationCommandService, times(1))
			.sendChat(ADMIN_ID, ROOM_ID, "역전할머니 맥주", "안녕하세요");
	}

	// ===== readMessage =====

	@Test
	@DisplayName("안읽은 메시지가 없으면 읽음 이벤트를 브로드캐스트하지 않는다")
	void readMessage_NoUnreadMessages_NoBroadcast() {
		// 1. Given
		when(messageRepository.findUnreadMessagesByRoomAndReceiver(ROOM_ID, ADMIN_ID)).thenReturn(List.of());

		// 2. When
		ChatResponseDTO.ReadMessageResponseDTO response = chatService.readMessage(ROOM_ID, ADMIN_ID);

		// 3. Then
		assertTrue(response.readMessagesId().isEmpty());
		assertEquals(0, response.readCount());
		verify(simpMessagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	@DisplayName("안읽은 메시지들을 모두 읽음 처리하고 읽음 영수증을 브로드캐스트한다")
	void readMessage_UnreadMessages_MarksAsReadAndBroadcasts() {
		// 1. Given
		Message message1 = Message.builder().id(100L).unreadCount(1).isRead(false).build();
		Message message2 = Message.builder().id(101L).unreadCount(1).isRead(false).build();
		when(messageRepository.findUnreadMessagesByRoomAndReceiver(ROOM_ID, ADMIN_ID))
			.thenReturn(List.of(message1, message2));

		// 2. When
		ChatResponseDTO.ReadMessageResponseDTO response = chatService.readMessage(ROOM_ID, ADMIN_ID);

		// 3. Then
		assertEquals(List.of(100L, 101L), response.readMessagesId());
		assertEquals(2, response.readCount());

		// 엔티티가 실제로 읽음 처리되었는지
		assertTrue(message1.isRead());
		assertEquals(0, message1.getUnreadCount());
		assertTrue(message2.isRead());

		verify(simpMessagingTemplate).convertAndSend(
			eq("/sub/chat/" + ROOM_ID),
			argThat((ChatResponseDTO.ReadMessageResponseDTO receipt) ->
				receipt.readMessagesId().equals(List.of(100L, 101L)) && receipt.readerId().equals(ADMIN_ID))
		);
	}

	// ===== leaveChattingRoom =====

	@Test
	@DisplayName("두 명이 있는 채팅방에서 관리자가 나가면 인원이 1명으로 줄고 방은 유지된다")
	void leaveChattingRoom_TwoMembers_AdminLeaves() {
		// 1. Given
		Member adminMember = givenMember(ADMIN_ID);
		Admin admin = Admin.builder().id(ADMIN_ID).member(adminMember).name("총학생회").isPhoneVerified(false).build();
		Partner partner = givenPartner(PARTNER_ID);

		ChattingRoom room = ChattingRoom.builder()
			.id(ROOM_ID).admin(admin).partner(partner).memberCount(2).build();
		when(chatRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

		// 2. When
		ChatResponseDTO.LeaveChattingRoomResponseDTO response =
			chatService.leaveChattingRoom(ROOM_ID, ADMIN_ID);

		// 3. Then
		assertTrue(response.isLeftSuccessfully());
		assertFalse(response.isRoomDeleted());
		assertNull(room.getAdmin());
		assertEquals(1, room.getMemberCount());
		verify(chatRepository, times(1)).save(room);
		verify(chatRepository, never()).delete(any());
	}

	@Test
	@DisplayName("마지막 남은 인원이 나가면 채팅방이 삭제된다")
	void leaveChattingRoom_LastMemberLeaves_DeletesRoom() {
		// 1. Given (파트너는 이미 나갔고 관리자만 남은 방)
		Member adminMember = givenMember(ADMIN_ID);
		Admin admin = Admin.builder().id(ADMIN_ID).member(adminMember).name("총학생회").isPhoneVerified(false).build();

		ChattingRoom room = ChattingRoom.builder()
			.id(ROOM_ID).admin(admin).partner(null).memberCount(1).build();
		when(chatRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

		// 2. When
		ChatResponseDTO.LeaveChattingRoomResponseDTO response =
			chatService.leaveChattingRoom(ROOM_ID, ADMIN_ID);

		// 3. Then
		assertTrue(response.isLeftSuccessfully());
		assertTrue(response.isRoomDeleted());
		verify(chatRepository, times(1)).delete(room);
		verify(chatRepository, never()).save(any());
	}

	@Test
	@DisplayName("채팅방 구성원이 아닌 회원이 나가려고 하면 NO_SUCH_MEMBER 예외가 발생한다")
	void leaveChattingRoom_NotAMember_ThrowsException() {
		// 1. Given
		Member stranger = givenMember(999L);
		Admin admin = givenAdmin(ADMIN_ID);
		Partner partner = givenPartner(PARTNER_ID);

		ChattingRoom room = ChattingRoom.builder()
			.id(ROOM_ID).admin(admin).partner(partner).memberCount(2).build();
		when(chatRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> chatService.leaveChattingRoom(ROOM_ID, 999L));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_MEMBER, exception.getCode());
	}
}
