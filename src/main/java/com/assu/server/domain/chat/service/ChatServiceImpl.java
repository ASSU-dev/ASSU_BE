package com.assu.server.domain.chat.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.chat.dto.*;
import com.assu.server.domain.chat.entity.ChattingRoom;
import com.assu.server.domain.chat.entity.Message;
import com.assu.server.domain.chat.repository.BlockRepository;
import com.assu.server.domain.chat.entity.enums.ChatEventType;
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
import com.assu.server.global.exception.GeneralException;
import com.assu.server.global.util.PresenceTracker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.assu.server.global.util.WebSocketBroadcaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final ChatRepository chatRepository;
    private final MemberRepository memberRepository;
    private final PartnerRepository partnerRepository;
    private final AdminRepository adminRepository;
    private final MessageRepository messageRepository;
    private final StoreRepository storeRepository;
    private final WebSocketBroadcaster webSocketBroadcaster;
    private final NotificationCommandService notificationCommandService;
    private final PresenceTracker presenceTracker;
    private final BlockRepository blockRepository;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;


    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomListResultDTO> getChatRoomList(Long memberId) {
        List<ChatRoomListResultDTO> chatRoomList = chatRepository.findChattingRoomsByMemberId(memberId);
        return ChatRoomListResultDTO.ofList(chatRoomList);
    }

    // 제약 위반을 트랜잭션 안에서 잡으면 rollback-only가 되어 커밋에서 실패하므로, 롤백이 끝난 뒤 새 트랜잭션에서 기존 방을 조회한다.
    @Override
    public ChatResponseDTO.CreateChatRoomResponseDTO createChatRoom(ChatRequestDTO.CreateChatRoomRequestDTO request, Long memberId) {
        try {
            return transactionTemplate.execute(status -> findOrCreateChatRoom(request));
        } catch (DataIntegrityViolationException e) {
            ChatResponseDTO.CreateChatRoomResponseDTO existingRoom = transactionTemplate.execute(status -> {
                ChattingRoom existing = chatRepository.findChattingRoomByAdminIdAndPartnerId(request.adminId(), request.partnerId());
                return existing == null ? null : ChatResponseDTO.CreateChatRoomResponseDTO.fromExistingRoom(existing);
            });
            if (existingRoom == null) {
                throw e;
            }
            return existingRoom;
        }
    }

    private ChatResponseDTO.CreateChatRoomResponseDTO findOrCreateChatRoom(ChatRequestDTO.CreateChatRoomRequestDTO request) {

        Long adminId = request.adminId();
        Long partnerId = request.partnerId();

        Admin admin = adminRepository.findByIdAndMember_DeletedAtIsNull(adminId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ADMIN));
        Partner partner = partnerRepository.findByIdAndMember_DeletedAtIsNull(partnerId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_PARTNER));
        Store store = storeRepository.findByPartnerId(partnerId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STORE));

        if (!store.getPartner().getMember().getId().equals(partner.getMember().getId())) {
            throw new DatabaseException(ErrorStatus.NO_SUCH_STORE_WITH_THAT_PARTNER);
        }

        ChattingRoom existChatRoom = chatRepository.findChattingRoomByAdminIdAndPartnerId(admin.getId(), partner.getId());
        if (existChatRoom != null) {
            return ChatResponseDTO.CreateChatRoomResponseDTO.fromExistingRoom(existChatRoom);
        }

        ChattingRoom room = ChattingRoom.of(admin, partner);
        room.updateStatus(ActivationStatus.ACTIVE);
        room.updateMemberCount(2);
        room.updateName(partner.getName(), admin.getName());
        ChattingRoom savedRoom = chatRepository.save(room);
        return ChatResponseDTO.CreateChatRoomResponseDTO.fromNewRoom(savedRoom);
    }

    @Override
    @Transactional
    public MessageHandlingResultDTO handleMessage(ChatRequestDTO.ChatMessageRequestDTO request, Long authenticatedMemberId) {
        // 1. 유효성 검사 (기존 로직)
        ChattingRoom room = chatRepository.findById(request.roomId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ROOM));
        if (!request.senderId().equals(authenticatedMemberId)
                || !isRoomParticipants(room, request.senderId(), request.receiverId())) {
            throw new DatabaseException(ErrorStatus.NO_MEMBER_IN_THE_ROOM);
        }
        Member sender = memberRepository.findById(request.senderId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));
        Member receiver = memberRepository.findById(request.receiverId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));
        if (sender.isWithdrawn() || receiver.isWithdrawn()) {
            throw new DatabaseException(ErrorStatus.NO_SUCH_MEMBER);
        }

        // 차단 검증 — 운영자가 개인 차단하거나 두 멤버 사이를 차단한 경우 메시지 전송 불가
        if (Boolean.TRUE.equals(sender.getChatBlocked())) {
            throw new GeneralException(ErrorStatus.MEMBER_CHAT_BLOCKED);
        }
        if (blockRepository.existsBlockRelationBetween(sender, receiver)) {
            throw new GeneralException(ErrorStatus.MEMBER_CHAT_BLOCKED);
        }

        // 2. 컨트롤러에서 가져온 비즈니스 로직 (접속 확인)
        boolean receiverInRoom = presenceTracker.isInRoom(request.receiverId(), request.roomId());
        int unreadForSender = receiverInRoom ? 0 : 1;

        // 3. 메시지 저장 (기존 로직)
        Message message = Message.toMessageEntity(request.message(), room, sender, receiver, unreadForSender);
        Message saved = messageRepository.saveAndFlush(message);
        incrementAfterCommit(meterRegistry.counter("chat.message.sent"));
        log.info("saved message id={}, roomId={}, senderId={}, receiverId={}",
                saved.getId(), room.getId(), sender.getId(), receiver.getId());

        ChatResponseDTO.SendMessageResponseDTO savedDTO = ChatResponseDTO.SendMessageResponseDTO.from(saved);

        // 4. 컨트롤러에서 가져온 비즈니스 로직 (수신자 부재 시)
        if (!receiverInRoom) {
            // 4-1. 안 읽은 수 계산
            Long totalUnreadCount = messageRepository.countUnreadMessagesByRoomAndReceiver(
                    request.roomId(),
                    request.receiverId()
            );

            // 4-2. 채팅방 목록 업데이트 DTO 생성
            ChatRoomUpdateDTO updateDTO = ChatRoomUpdateDTO.of(
                    request.roomId(),
                    savedDTO.message(),
                    savedDTO.sentAt(),
                    totalUnreadCount
            );

            // 4-3. 발신자 이름 찾기 (기존 컨트롤러 로직)
            String senderName = sender.resolveName();

            // 4-4. 알림 전송
            notificationCommandService.sendChat(request.receiverId(), request.roomId(), senderName, request.message());

            // 5. [업데이트 포함] 결과 반환
            return MessageHandlingResultDTO.withUpdates(savedDTO, updateDTO, request.receiverId());
        }

        // 5. [일반 메시지] 결과 반환
        return MessageHandlingResultDTO.of(savedDTO);
    }


    @Override
    @Transactional
    public ChatResponseDTO.SendMessageResponseDTO sendGuideMessage(ChatRequestDTO.ChatMessageRequestDTO request) {
        // 유효성 검사
        ChattingRoom room = chatRepository.findById(request.roomId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ROOM));
        Member sender = memberRepository.findById(request.senderId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));
        Member receiver = memberRepository.findById(request.receiverId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));

        Message message = Message.toGuideMessageEntity(request.message(), room, sender, receiver);
        Message saved = messageRepository.saveAndFlush(message);

        ChatResponseDTO.SendMessageResponseDTO responseDTO = ChatResponseDTO.SendMessageResponseDTO.from(saved);
        webSocketBroadcaster.sendAfterCommit("/sub/chat/" + request.roomId(), responseDTO);

        return responseDTO;
    }

    @Transactional
    @Override
    public ChatResponseDTO.ReadMessageResponseDTO readMessage(Long roomId, Long memberId) {

        List<Message> unreadMessages = messageRepository.findUnreadMessagesByRoomAndReceiver(roomId, memberId);
        List<Long> readMessagesIdList = new ArrayList<>();

        for(Message unreadMessage : unreadMessages) {
            readMessagesIdList.add(unreadMessage.getId());
        }
        unreadMessages.forEach(Message::markAsRead);

        if (!readMessagesIdList.isEmpty()) {
            ChatResponseDTO.ReadMessageResponseDTO receipt =
                    new ChatResponseDTO.ReadMessageResponseDTO(ChatEventType.READ_RECEIPT, roomId, memberId, readMessagesIdList, unreadMessages.size(), true);
            webSocketBroadcaster.sendAfterCommit("/sub/chat/" + roomId, receipt);
        }

        return new ChatResponseDTO.ReadMessageResponseDTO(ChatEventType.READ_RECEIPT, roomId, memberId, readMessagesIdList, unreadMessages.size(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatResponseDTO.ChatHistoryResponseDTO readHistory(Long roomId, Long memberId) {

        ChattingRoom room = chatRepository.findById(roomId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ROOM));

        List<ChatMessageDTO> allMessages = messageRepository.findAllMessagesByRoomAndMemberId(room.getId(), memberId);

        return ChatResponseDTO.ChatHistoryResponseDTO.of(room.getId(), allMessages);
    }

    @Override
    @Transactional
    public ChatResponseDTO.LeaveChattingRoomResponseDTO leaveChattingRoom(Long roomId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));

        ChattingRoom chattingRoom = chatRepository.findWithLockById(roomId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_MEMBER_IN_THE_ROOM));

        boolean isAdmin = chattingRoom.getAdmin() != null &&
                chattingRoom.getAdmin().getMember().getId().equals(member.getId());
        boolean isPartner = chattingRoom.getPartner() != null &&
                chattingRoom.getPartner().getMember().getId().equals(member.getId());

        if (isAdmin) {
            chattingRoom.removeAdmin();
        } else if (isPartner) {
            chattingRoom.removePartner();
        } else if (hasAlreadyLeft(chattingRoom, member)) {
            return new ChatResponseDTO.LeaveChattingRoomResponseDTO(roomId, true, false);
        } else {
            throw new DatabaseException(ErrorStatus.NO_SUCH_MEMBER);
        }

        int remainingCount = (chattingRoom.getAdmin() != null ? 1 : 0) + (chattingRoom.getPartner() != null ? 1 : 0);
        chattingRoom.updateMemberCount(remainingCount);

        if (remainingCount == 0) {
            chatRepository.delete(chattingRoom);
            return new ChatResponseDTO.LeaveChattingRoomResponseDTO(roomId, true, true);
        }
        chatRepository.save(chattingRoom);
        return new ChatResponseDTO.LeaveChattingRoomResponseDTO(roomId, true, false);
    }

    // 나간 쪽 FK는 null로 비워지므로, 요청자 역할의 자리가 비어 있으면 이미 나간 참여자의 재요청으로 본다.
    private boolean hasAlreadyLeft(ChattingRoom chattingRoom, Member member) {
        return (member.getRole() == UserRole.ADMIN && chattingRoom.getAdmin() == null)
                || (member.getRole() == UserRole.PARTNER && chattingRoom.getPartner() == null);
    }

    private boolean isRoomParticipants(ChattingRoom room, Long senderId, Long receiverId) {
        if (room.getAdmin() == null || room.getPartner() == null) {
            return false;
        }
        Long adminId = room.getAdmin().getId();
        Long partnerId = room.getPartner().getId();
        return (adminId.equals(senderId) && partnerId.equals(receiverId))
                || (partnerId.equals(senderId) && adminId.equals(receiverId));
    }

    private void incrementAfterCommit(Counter counter) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            counter.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment();
            }
        });
    }
}
