package com.assu.server.domain.partnership.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import com.assu.server.domain.chat.dto.ChatRequestDTO;
import com.assu.server.domain.chat.entity.ChattingRoom;
import com.assu.server.domain.chat.repository.ChatRepository;
import com.assu.server.domain.chat.service.ChatService;
import org.springframework.stereotype.Service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.partnership.converter.PartnershipConverter;
import com.assu.server.domain.partnership.dto.PartnershipRequestDTO;
import com.assu.server.domain.user.entity.PartnershipUsage;
import com.assu.server.domain.user.entity.Student;
import com.assu.server.domain.user.repository.PartnershipUsageRepository;
import com.assu.server.domain.user.repository.StudentRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.partnership.dto.PartnershipResponseDTO;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.GoodsRepository;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.infra.s3.AmazonS3Manager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;



@Service
@Transactional
@RequiredArgsConstructor
public class PartnershipServiceImpl implements PartnershipService {

	private final PartnershipUsageRepository partnershipUsageRepository;
	private final StudentRepository studentRepository;
    private final PaperContentRepository contentRepository;
    private final NotificationCommandService notificationService;
    private final ChatService chatService;
    private final ChatRepository chatRepository;


    @Override
    @Transactional
    public void recordPartnershipUsage(PartnershipRequestDTO.finalRequest dto, Member member) {
        // 1. 제휴 내용(PaperContent) 조회
        PaperContent content = contentRepository.findById(dto.getContentId()).orElseThrow(
            () -> new GeneralException(ErrorStatus.NO_SUCH_CONTENT)
        );
        Long paperId = content.getPaper().getId();

        // 2. 중복을 허용하지 않는 Set을 사용하여 모든 사용자 ID를 수집
        Set<Long> uniqueUserIds = new HashSet<>();
        // 요청자 본인 ID 추가
        uniqueUserIds.add(member.getId());
        // DTO에 포함된 사용자 ID들 추가 (null일 경우 무시)
        if (dto.getUserIds() != null) {
            uniqueUserIds.addAll(dto.getUserIds());
        }

        // 3. 모든 학생 정보를 DB에서 한 번의 쿼리로 조회 (N+1 문제 해결)
        List<Student> studentsToUpdate = studentRepository.findAllById(uniqueUserIds);

        // 4. 조회된 학생들에 대해 PartnershipUsage 생성 및 스탬프 업데이트
        List<PartnershipUsage> usages = studentsToUpdate.stream()
            .map(student -> {
                student.setStamp();
                return PartnershipConverter.toPartnershipUsage(dto, student, paperId);
            })
            .collect(Collectors.toList());

        // 5. 생성된 모든 Usage 기록을 한 번에 저장
        partnershipUsageRepository.saveAll(usages);
        Store store = storeRepository.findById(dto.getStoreId()).orElseThrow(
            () -> new GeneralException(ErrorStatus.NO_SUCH_STORE)
        );
        Partner partner = store.getPartner();
        Long partnerId = partner.getId();
        notificationService.sendOrder(partnerId, 0L, dto.getTableNumber(), dto.getPartnershipContent());

        // @Transactional 환경에서는 studentsToUpdate의 변경 사항(스탬프)이 자동으로 DB에 반영됩니다.
    }




    private final PaperRepository paperRepository;
    private final PaperContentRepository paperContentRepository;
    private final GoodsRepository goodsRepository;

    private final AdminRepository adminRepository;
    private final PartnerRepository partnerRepository;
    private final StoreRepository storeRepository;

    private final AmazonS3Manager amazonS3Manager;

    @Override
    @Transactional
    public PartnershipResponseDTO.WritePartnershipResponseDTO updatePartnership(
            PartnershipRequestDTO.WritePartnershipRequestDTO request,
            Long memberId
    ) {
        if (request == null || memberId == null) {
            throw new DatabaseException(ErrorStatus._BAD_REQUEST);
        }

        Paper paper = paperRepository.findById(request.getPaperId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_PAPER));

        Partner partner = partnerRepository.findById(memberId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_PARTNER));

        Admin admin = adminRepository.findById(paper.getAdmin().getId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ADMIN));

        Store store = storeRepository.findByPartner(partner)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STORE));

        PartnershipConverter.updatePaperFromDto(paper, request);

        List<PaperContent> existingContents = paperContentRepository.findByPaperId(request.getPaperId());
        if (!existingContents.isEmpty()) {
            List<Long> contentIds = existingContents.stream().map(PaperContent::getId).toList();
            goodsRepository.deleteAllByContentIds(contentIds);
            paperContentRepository.deleteAll(existingContents);
        }

        List<PaperContent> newContents = PartnershipConverter.toPaperContents(request, paper);
        newContents = newContents.isEmpty() ? newContents : paperContentRepository.saveAll(newContents);

        List<List<Goods>> requestGoodsBatches = PartnershipConverter.toGoodsBatches(request);

        List<List<Goods>> attachedGoodsBatches = new ArrayList<>();
        List<Goods> toPersist = new ArrayList<>();

        for (int i = 0; i < newContents.size(); i++) {
            PaperContent content = newContents.get(i);
            List<Goods> batch = (requestGoodsBatches.size() > i) ? requestGoodsBatches.get(i) : Collections.emptyList();
            List<Goods> attached = new ArrayList<>();
            for (Goods g : batch) {
                Goods entity = Goods.builder()
                        .content(content)
                        .belonging(g.getBelonging())
                        .build();
                attached.add(entity);
                toPersist.add(entity);
            }
            attachedGoodsBatches.add(attached);
        }
        if (!toPersist.isEmpty()) {
            goodsRepository.saveAll(toPersist);
        }

        return PartnershipConverter.writePartnershipResultDTO(paper, newContents, attachedGoodsBatches);
    }

    @Override
    public List<PartnershipResponseDTO.WritePartnershipResponseDTO> listPartnershipsForAdmin(boolean all, Long adminId) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        List<Paper> papers = all
                ? paperRepository.findByAdmin_IdAndIsActivated(adminId, ActivationStatus.ACTIVE, sort)
                : paperRepository.findByAdmin_IdAndIsActivated(adminId, ActivationStatus.ACTIVE, PageRequest.of(0, 2, sort)).getContent();

        papers = papers.stream()
                .filter(p -> p.getStore() != null)
                .toList();

        return buildPartnershipDTOs(papers);
    }

    @Override
    public List<PartnershipResponseDTO.WritePartnershipResponseDTO> listPartnershipsForPartner(boolean all, Long partnerId) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        List<Paper> papers = all
                ? paperRepository.findByPartner_IdAndIsActivated(partnerId, ActivationStatus.ACTIVE, sort)
                : paperRepository.findByPartner_IdAndIsActivated(partnerId, ActivationStatus.ACTIVE, PageRequest.of(0, 2, sort)).getContent();

        papers = papers.stream()
                .filter(p -> p.getAdmin() != null)
                .toList();

        return buildPartnershipDTOs(papers);
    }

    @Override
    @Transactional
    public PartnershipResponseDTO.GetPartnershipDetailResponseDTO getPartnership(Long partnershipId) {
        Paper paper = paperRepository.findById(partnershipId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_PAPER));

        List<PaperContent> contents = paperContentRepository.findAllByOnePaperIdInFetchGoods(partnershipId);

        List<List<Goods>> goodsBatches = contents.stream()
                .map(pc -> pc.getGoods() == null ? Collections.<Goods>emptyList() : pc.getGoods())
                .toList();

        return PartnershipConverter.getPartnershipResultDTO(paper, contents, goodsBatches);
    }

    @Override
    @Transactional
    public List<PartnershipResponseDTO.SuspendedPaperDTO> getSuspendedPapers(Long adminId) {
        List<Paper> suspendedPapers =
                paperRepository.findAllSuspendedByAdminWithNoPartner(ActivationStatus.SUSPEND, adminId);

        return suspendedPapers.stream()
                .map(paper -> PartnershipResponseDTO.SuspendedPaperDTO.builder()
                        .paperId(paper.getId())
                        .partnerName(
                                paper.getPartner() != null
                                        ? paper.getPartner().getName()
                                        : (paper.getStore() != null ? paper.getStore().getName() : "미등록")
                        )
                        .createdAt(paper.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public PartnershipResponseDTO.UpdateResponseDTO updatePartnershipStatus(Long partnershipId, PartnershipRequestDTO.UpdateRequestDTO request) {
        Paper paper = paperRepository.findById(partnershipId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_PAPER));

        if(request == null || request.getStatus() == null){
            throw new DatabaseException(ErrorStatus._BAD_REQUEST);
        }

        ActivationStatus prev = paper.getIsActivated();
        ActivationStatus next = parseStatus(request.getStatus());

        paper.setIsActivated(next);

        Long adminId = paper.getAdmin().getId();
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ADMIN));
        Long partnerId = paper.getPartner().getId();
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_PARTNER));
        ChattingRoom chattingRoom = chatRepository.findChattingRoomByAdminIdAndPartnerId(admin.getId(), partner.getId());

        if (next.equals(ActivationStatus.SUSPEND)) {
            String guideMessage = partner.getName() + "님이 제휴 제안서를 전송했어요!\n내용을 확인 후 동의해 주세요";
            ChatRequestDTO.ChatMessageRequestDTO guideMessageRequest = new ChatRequestDTO.ChatMessageRequestDTO(
                    chattingRoom.getId(),
                    partnerId,
                    adminId,
                    guideMessage
            );
            chatService.sendGuideMessage(guideMessageRequest);
            notificationService.sendChat(adminId, chattingRoom.getId(), partner.getName(), guideMessage);

        } else if (next.equals(ActivationStatus.ACTIVE)) {
            String guideMessage = "축하드립니다!\n" + "제휴 계약이 성립되었습니다. 제휴 계약서를 다시한번 확인해 보세요!";
            ChatRequestDTO.ChatMessageRequestDTO guideMessageRequest = new ChatRequestDTO.ChatMessageRequestDTO(
                    chattingRoom.getId(),
                    adminId,
                    partnerId,
                    guideMessage
            );
            chatService.sendGuideMessage(guideMessageRequest);
            notificationService.sendChat(partnerId, chattingRoom.getId(), admin.getName(), guideMessage);
        }

        return PartnershipResponseDTO.UpdateResponseDTO.builder()
                .partnershipId(paper.getId())
                .prevStatus(prev == null ? null : prev.name())
                .newStatus(next.name())
                .changedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public PartnershipResponseDTO.ManualPartnershipResponseDTO createManualPartnership(
            PartnershipRequestDTO.ManualPartnershipRequestDTO request,
            Long adminId,
            MultipartFile contractImage) {

        if (request == null || adminId == null)
            throw new DatabaseException(ErrorStatus._BAD_REQUEST);

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ADMIN));

        String address = pickDisplayAddress(request.getSelectedPlace().getRoadAddress(), request.getSelectedPlace().getAddress());

        Store store = storeRepository
                .findByNameAndAddressAndDetailAddress(request.getStoreName(), address, request.getStoreDetailAddress())
                .orElse(null);

        boolean created = false;
        boolean reactivated = false;

        if (store == null) {
            store = Store.builder()
                    .name(request.getStoreName())
                    .address(address)
                    .detailAddress(request.getStoreDetailAddress())
                    .rate(0)
                    .isActivate(ActivationStatus.SUSPEND)
                    .build();
            store = storeRepository.save(store);
            created = true;
        } else if (store.getIsActivate() == ActivationStatus.INACTIVE) {
            store.setIsActivate(ActivationStatus.SUSPEND);
            reactivated = true;
        }

        Paper paper = PartnershipConverter.toPaperForManual(
                admin, store,
                request.getPartnershipPeriodStart(),
                request.getPartnershipPeriodEnd(),
                ActivationStatus.SUSPEND
        );
        paper = paperRepository.save(paper);

        if (contractImage != null && !contractImage.isEmpty()) {
            try {
                String keyName = amazonS3Manager.generateKeyName("contract-images");
                amazonS3Manager.uploadFile(keyName, contractImage);
                paper.updateContractImageKey(keyName);
                paperRepository.save(paper);
            } catch (Exception e) {
                throw new DatabaseException(ErrorStatus.IMAGE_UPLOAD_FAILED);
            }
        }

        List<PaperContent> savedContents = new ArrayList<>();
        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            List<PaperContent> contents = PartnershipConverter.toPaperContentsForManual(request.getOptions(), paper);
            savedContents = paperContentRepository.saveAll(contents);

            List<Goods> toPersist = new ArrayList<>();
            for (int i = 0; i < savedContents.size(); i++) {
                var opt = request.getOptions().get(i);
                var content = savedContents.get(i);
                var batch = PartnershipConverter.toGoodsForContent(opt, content);
                if (!batch.isEmpty()) toPersist.addAll(batch);
            }
            if (!toPersist.isEmpty()) goodsRepository.saveAll(toPersist);
        }

        List<PaperContent> contentsWithGoods = paperContentRepository.findAllByOnePaperIdInFetchGoods(paper.getId());
        List<List<Goods>> goodsBatches = contentsWithGoods.stream()
                .map(pc -> pc.getGoods() == null ? List.<Goods>of() : pc.getGoods())
                .toList();

        var partnership = PartnershipConverter.writePartnershipResultDTO(paper, contentsWithGoods, goodsBatches);

        String url = (paper.getContractImageKey() == null)
                ? null
                :amazonS3Manager.generatePresignedUrl(paper.getContractImageKey());

        return PartnershipResponseDTO.ManualPartnershipResponseDTO.builder()
                .storeId(store.getId())
                .storeCreated(created)
                .storeActivated(reactivated)
                .status(store.getIsActivate() == null ? null : store.getIsActivate().name())
                .contractImageUrl(url)
                .partnership(partnership)
                .build();
    }

    @Override
    @Transactional
    public PartnershipResponseDTO.CreateDraftResponseDTO createDraftPartnership(PartnershipRequestDTO.CreateDraftRequestDTO request, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ADMIN));
        Partner partner = partnerRepository.findById(request.getPartnerId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_PARTNER));
        Store store = storeRepository.findByPartner(partner)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STORE));

        Paper draftPaper = PartnershipConverter.toDraftPaperEntity(admin, partner, store);
        paperRepository.save(draftPaper);

        notificationService.sendPartnerProposal(partner.getId(), draftPaper.getId(), admin.getName());

        ChattingRoom chattingRoom = chatRepository.findChattingRoomByAdminIdAndPartnerId(admin.getId(), partner.getId());

        String guideMessage = admin.getName() + "님이 제휴 제안서 초안을 전송했어요! 확인 후 제휴 제안서를 작성해 주세요";
        ChatRequestDTO.ChatMessageRequestDTO guideMessageRequest = new ChatRequestDTO.ChatMessageRequestDTO(
                chattingRoom.getId(),
                admin.getId(),
                partner.getId(),
                guideMessage
        );

        // 5. 완성된 DTO를 사용해서 안내 메시지를 전송합니다.
        chatService.sendGuideMessage(guideMessageRequest);
        notificationService.sendChat(partner.getId(), chattingRoom.getId(), admin.getName(), guideMessage);

        return PartnershipConverter.toCreateDraftResponseDTO(draftPaper);
    }

    @Override
    @Transactional
    public void deletePartnership(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_PAPER));

        // 0. paperContent + goods 삭제
        List<PaperContent> contentsToDelete = paperContentRepository.findByPaperId(paperId);
        if (contentsToDelete != null && !contentsToDelete.isEmpty()) {
            List<Long> contentIds = contentsToDelete.stream()
                    .map(PaperContent::getId)
                    .toList();

            goodsRepository.deleteAllByContentIds(contentIds);
            paperContentRepository.deleteAll(contentsToDelete);
        }

        // 1. store 참조를 미리 잡아두기 (paper 삭제 후 사용)
        Store store = paper.getStore();
        boolean isTempStore = (store != null && paper.getPartner() == null);

        // 2. paper 삭제
        paperRepository.delete(paper);

        // 3) 임시 store 삭제 (재사용 중이면 보존)
        if (isTempStore) {
            Long storeId = store.getId();

            // 남은 paper 참조 수
            long remainingPaperRefs = paperRepository.countByStore_Id(storeId);

            if (remainingPaperRefs == 0) {
                storeRepository.delete(store);
            }
        }

    }

    @Override
    @Transactional
    public PartnershipResponseDTO.AdminPartnershipWithPartnerResponseDTO checkPartnershipWithPartner(Long adminId, Long partnerId) {

        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_PARTNER));

        List<ActivationStatus> targetStatuses = List.of(ActivationStatus.ACTIVE, ActivationStatus.SUSPEND, ActivationStatus.BLANK);
        boolean isPartnered = paperRepository.existsByAdmin_IdAndPartner_IdAndIsActivatedIn(adminId, partnerId, targetStatuses);

        Long paperId = null;
        String status = "NONE";

        if (isPartnered) {
            Optional<Paper> latestActiveOrSuspendPaper = paperRepository
                    .findTopByAdmin_IdAndPartner_IdAndIsActivatedInOrderByIdDesc(adminId, partnerId, targetStatuses);

            if (latestActiveOrSuspendPaper.isPresent()) {
                Paper paper = latestActiveOrSuspendPaper.get();
                paperId = paper.getId();
                status = paper.getIsActivated().name();
            }
        }

        return PartnershipResponseDTO.AdminPartnershipWithPartnerResponseDTO.builder()
                .paperId(paperId)
                .isPartnered(isPartnered)
                .status(status)
                .partnerId(partner.getId())
                .partnerName(partner.getName())
                .partnerAddress(partner.getAddress())
                .build();
    }

    @Override
    @Transactional
    public PartnershipResponseDTO.PartnerPartnershipWithAdminResponseDTO checkPartnershipWithAdmin(Long partnerId, Long adminId) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ADMIN));

        List<ActivationStatus> targetStatuses = List.of(ActivationStatus.ACTIVE, ActivationStatus.SUSPEND, ActivationStatus.BLANK);
        boolean isPartnered = paperRepository.existsByAdmin_IdAndPartner_IdAndIsActivatedIn(adminId, partnerId, targetStatuses);

        Long paperId = null;
        String status = "NONE";

        if (isPartnered) {
            Optional<Paper> latestActiveOrSuspendPaper = paperRepository
                    .findTopByAdmin_IdAndPartner_IdAndIsActivatedInOrderByIdDesc(adminId, partnerId, targetStatuses);

            if (latestActiveOrSuspendPaper.isPresent()) {
                Paper paper = latestActiveOrSuspendPaper.get();
                paperId = paper.getId();
                status = paper.getIsActivated().name();
            }
        }

        return PartnershipResponseDTO.PartnerPartnershipWithAdminResponseDTO.builder()
                .paperId(paperId)
                .isPartnered(isPartnered)
                .status(status)
                .adminId(admin.getId())
                .adminName(admin.getName())
                .adminAddress(admin.getOfficeAddress())
                .build();
    }

    private List<PartnershipResponseDTO.WritePartnershipResponseDTO> buildPartnershipDTOs(List<Paper> papers) {
        if (papers == null || papers.isEmpty()) return List.of();

        List<Long> paperIds = papers.stream().map(Paper::getId).toList();
        List<PaperContent> allContents = paperContentRepository.findAllByPaperIdInFetchGoods(paperIds);

        Map<Long, List<PaperContent>> byPaperId = allContents.stream()
                .collect(Collectors.groupingBy(pc -> pc.getPaper().getId()));

        List<PartnershipResponseDTO.WritePartnershipResponseDTO> result = new ArrayList<>(papers.size());
        for (Paper p : papers) {
            List<PaperContent> contents = byPaperId.getOrDefault(p.getId(), List.of());
            List<List<Goods>> goodsBatches = contents.stream()
                    .map(pc -> pc.getGoods() == null ? List.<Goods>of() : pc.getGoods())
                    .toList();
            result.add(PartnershipConverter.writePartnershipResultDTO(p, contents, goodsBatches));
        }
        return result;
    }

    private ActivationStatus parseStatus(String raw) {
        try {
            return ActivationStatus.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new DatabaseException(ErrorStatus._BAD_REQUEST);
        }
    }

    private String pickDisplayAddress(String road, String jibun) {
        return (road != null && !road.isBlank()) ? road : jibun;
    }
}
