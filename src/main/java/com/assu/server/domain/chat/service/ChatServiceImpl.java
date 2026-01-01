package com.assu.server.domain.chat.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.chat.dto.*;
import com.assu.server.domain.chat.entity.ChattingRoom;
import com.assu.server.domain.chat.entity.Message;
import com.assu.server.domain.chat.repository.ChatRepository;
import com.assu.server.domain.chat.repository.MessageRepository;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.util.PresenceTracker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

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
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final NotificationCommandService notificationCommandService;
    private final PresenceTracker presenceTracker;


    @Override
    public List<ChatRoomListResultDTO> getChatRoomList(Long memberId) {

        List<ChatRoomListResultDTO> chatRoomList = chatRepository.findChattingRoomsByMemberId(memberId);
        return ChatRoomListResultDTO.toChatRoomListResultDTO(chatRoomList);
    }

    @Override
    public ChatResponseDTO.CreateChatRoomResponseDTO createChatRoom(ChatRequestDTO.CreateChatRoomRequestDTO request, Long memberId) {

        Long adminId = request.adminId();
        Long partnerId = request.partnerId();

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ADMIN));
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_PARTNER));
        Store store = storeRepository.findByPartnerId(partnerId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STORE));


        if (!store.getPartner().getMember().getId().equals(partner.getMember().getId())) {
            throw new DatabaseException(ErrorStatus.NO_SUCH_STORE_WITH_THAT_PARTNER);
        }

        boolean isExist = chatRepository.checkChattingRoomByAdminIdAndPartnerId(admin.getId(), partner.getId());

        if(!isExist) {
            ChattingRoom room = ChattingRoom.toCreateChattingRoom(admin, partner);

            room.updateStatus(ActivationStatus.ACTIVE);

            room.updateMemberCount(2);

            room.updateName(
                    partner.getName(),
                    admin.getName()
            );
            ChattingRoom savedRoom = chatRepository.save(room);
            return ChatResponseDTO.CreateChatRoomResponseDTO.toCreateChatRoomIdDTO(savedRoom);
        } else {
            ChattingRoom existChatRoom = chatRepository.findChattingRoomByAdminIdAndPartnerId(admin.getId(), partner.getId());
            return ChatResponseDTO.CreateChatRoomResponseDTO.toEnterChatRoomDTO(existChatRoom);
        }
    }

//    @Override
//    @Transactional
//    public ChatResponseDTO.SendMessageResponseDTO handleMessage(ChatRequestDTO.ChatMessageRequestDTO request) {
//        // 유효성 검사
//        ChattingRoom room = chatRepository.findById(request.getRoomId())
//                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ROOM));
//        Member sender = memberRepository.findById(request.getSenderId())
//                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));
//        Member receiver = memberRepository.findById(request.getReceiverId())
//                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));
//
//        Message message = ChatConverter.toMessageEntity(request, room, sender, receiver);
//        Message saved = messageRepository.saveAndFlush(message);
//        log.info("saved message id={}, roomId={}, senderId={}, receiverId={}",
//                saved.getId(), room.getId(), sender.getId(), receiver.getId());
//
//        return ChatConverter.toSendMessageDTO(saved);
//    }

    // ChatService의 handleMessage 메서드 (수정)

    @Override
    @Transactional
    public MessageHandlingResult handleMessage(ChatRequestDTO.ChatMessageRequestDTO request) {
        // 1. 유효성 검사 (기존 로직)
        ChattingRoom room = chatRepository.findById(request.roomId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ROOM));
        Member sender = memberRepository.findById(request.senderId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));
        Member receiver = memberRepository.findById(request.receiverId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));

        // 2. 컨트롤러에서 가져온 비즈니스 로직 (접속 확인)
        boolean receiverInRoom = presenceTracker.isInRoom(request.receiverId(), request.roomId());
        int unreadForSender = receiverInRoom ? 0 : 1;

        // 3. 메시지 저장 (기존 로직)
        Message message = Message.toMessageEntity(request, room, sender, receiver, unreadForSender);
        Message saved = messageRepository.saveAndFlush(message);
        log.info("saved message id={}, roomId={}, senderId={}, receiverId={}",
                saved.getId(), room.getId(), sender.getId(), receiver.getId());

        ChatResponseDTO.SendMessageResponseDTO savedDTO = ChatResponseDTO.SendMessageResponseDTO.toSendMessageDTO(saved);

        // 4. 컨트롤러에서 가져온 비즈니스 로직 (수신자 부재 시)
        if (!receiverInRoom) {
            // 4-1. 안 읽은 수 계산
            Long totalUnreadCount = messageRepository.countUnreadMessagesByRoomAndReceiver(
                    request.roomId(),
                    request.receiverId()
            );

            // 4-2. 채팅방 목록 업데이트 DTO 생성
            ChatRoomUpdateDTO updateDTO = ChatRoomUpdateDTO.toChatRoomUpdateDTO(
                    request.roomId(),
                    savedDTO.message(),
                    savedDTO.sentAt(),
                    totalUnreadCount);

            // 4-3. 발신자 이름 찾기 (기존 컨트롤러 로직)
            String senderName;
            if (sender.getRole() == UserRole.ADMIN) { // 이미 sender 객체가 있으므로 재활용
                senderName = sender.getAdminProfile().getName();
            } else {
                senderName = sender.getPartnerProfile().getName();
            }

            // 4-4. 알림 전송
            notificationCommandService.sendChat(request.receiverId(), request.roomId(), senderName, request.message());

            // 5. [업데이트 포함] 결과 반환
            return MessageHandlingResult.withUpdates(savedDTO, updateDTO, request.receiverId());
        }

        // 5. [일반 메시지] 결과 반환
        return MessageHandlingResult.of(savedDTO);
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

        Message message = Message.toGuideMessageEntity(request, room, sender, receiver);
        Message saved = messageRepository.saveAndFlush(message);

        ChatResponseDTO.SendMessageResponseDTO responseDTO = ChatResponseDTO.SendMessageResponseDTO.toSendMessageDTO(saved);
        simpMessagingTemplate.convertAndSend("/sub/chat/" + request.roomId(), responseDTO);

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


        return new ChatResponseDTO.ReadMessageResponseDTO(roomId, memberId,readMessagesIdList, unreadMessages.size(), true);
    }

    @Override
    public ChatResponseDTO.ChatHistoryResponseDTO readHistory(Long roomId, Long memberId) {

        ChattingRoom room = chatRepository.findById(roomId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ROOM));

        List<ChatMessageDTO> allMessages = messageRepository.findAllMessagesByRoomAndMemberId(room.getId(), memberId);

        return ChatResponseDTO.ChatHistoryResponseDTO.toChatHistoryDTO(room.getId(), allMessages);
    }

    @Override
    public ChatResponseDTO.LeaveChattingRoomResponseDTO leaveChattingRoom(Long roomId, Long memberId) {
        // 멤버 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));

        // 채팅방 조회
        ChattingRoom chattingRoom = chatRepository.findById(roomId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_MEMBER_IN_THE_ROOM));

        boolean isAdmin = chattingRoom.getAdmin() != null &&
                chattingRoom.getAdmin().getMember().getId().equals(member.getId());
        boolean isPartner = chattingRoom.getPartner() != null &&
                chattingRoom.getPartner().getMember().getId().equals(member.getId());

        int memberCount = chattingRoom.getMemberCount();
        boolean isRoomDeleted = false;
        boolean isLeftSuccessfully = false;

        if(memberCount == 2) {
            if (isAdmin) {
                chattingRoom.setAdmin(null);
            } else if (isPartner) {
                chattingRoom.setPartner(null);
            } else {
                throw new DatabaseException(ErrorStatus.NO_SUCH_MEMBER);
            }
            chattingRoom.updateMemberCount(1);
            isLeftSuccessfully = true;
            chatRepository.save(chattingRoom);
        } else if(memberCount == 1) {
            if (isAdmin) {
                chattingRoom.setAdmin(null);
            } else if (isPartner) {
                chattingRoom.setPartner(null);
            }
            chattingRoom.updateMemberCount(0);
            isLeftSuccessfully = true;

            // ✅ 방에 아무도 안 남았을 때만 삭제
            if (chattingRoom.getAdmin() == null && chattingRoom.getPartner() == null) {
                isRoomDeleted = true;
                chatRepository.delete(chattingRoom);
            } else {
                chatRepository.save(chattingRoom);
            }

        } else if(memberCount == 0) {
            throw new DatabaseException(ErrorStatus.NO_MEMBER);
        }
        return new  ChatResponseDTO.LeaveChattingRoomResponseDTO(roomId, isLeftSuccessfully,isRoomDeleted);
    }
}
