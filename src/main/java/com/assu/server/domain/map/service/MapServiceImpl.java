package com.assu.server.domain.map.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.map.dto.AdminMapResponseDTO;
import com.assu.server.domain.map.dto.MapRequestDTO;
import com.assu.server.domain.map.dto.PartnerMapResponseDTO;
import com.assu.server.domain.map.dto.StoreMapResponseDTO;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.user.entity.UserPaper;
import com.assu.server.domain.user.repository.UserPaperRepository;
import com.assu.server.infra.s3.AmazonS3Manager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapServiceImpl implements MapService {

    private final AdminRepository adminRepository;
    private final PartnerRepository partnerRepository;
    private final StoreRepository storeRepository;
    private final PaperContentRepository paperContentRepository;
    private final PaperRepository paperRepository;
    private final AmazonS3Manager amazonS3Manager;
    private final UserPaperRepository userPaperRepository;

    @Override
    public List<PartnerMapResponseDTO> getPartners(MapRequestDTO viewport, Long memberId) {
        String wkt = toWKT(viewport);
        List<Partner> partners = partnerRepository.findAllWithinViewportWithMember(wkt);

        if (partners.isEmpty()) {
            return List.of();
        }

        List<Long> partnerIds = partners.stream().map(Partner::getId).toList();
        List<Paper> papers = paperRepository.findByAdminIdAndPartnerIdInAndIsActivated(memberId, partnerIds, ActivationStatus.ACTIVE);
        Map<Long, Paper> partnerIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getPartner().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        return partners.stream()
                .map(p -> PartnerMapResponseDTO.of(p, partnerIdToPaper.get(p.getId()), amazonS3Manager))
                .toList();
    }

    @Override
    public List<AdminMapResponseDTO> getAdmins(MapRequestDTO viewport, Long memberId) {
        String wkt = toWKT(viewport);
        List<Admin> admins = adminRepository.findAllWithinViewportWithMember(wkt, PageRequest.of(0, 200));

        if (admins.isEmpty()) {
            return List.of();
        }

        List<Long> adminIds = admins.stream().map(Admin::getId).toList();
        List<Paper> papers = paperRepository.findByAdminIdInAndPartnerIdAndIsActivated(adminIds, memberId, ActivationStatus.ACTIVE);
        Map<Long, Paper> adminIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getAdmin().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        return admins.stream()
                .map(a -> AdminMapResponseDTO.of(a, adminIdToPaper.get(a.getId()), amazonS3Manager))
                .toList();
    }

    /**
     * 뷰포트 내 매장 조회 (학생 혜택 기반).
     *
     * 조회 기준:
     *  1. memberId(student_id)와 매치되는 모든 user_paper 조회
     *  2. 각 user_paper의 paper_id를 가진 paper 중 is_activated = ACTIVE인 것
     *  3. 그 중 뷰포트 내 store를 포함한 paper
     *
     * 위 기준을 만족하는 paper를 매장당 모두 가져와 partnerships 리스트로 반환.
     * papercontent의 note가 있으면 benefit 대신 note를 사용.
     */
    @Override
    public List<StoreMapResponseDTO> getStores(MapRequestDTO viewport, Long memberId) {
        final String wkt = toWKT(viewport);

        // 1) 뷰포트 내 매장 조회 (Partner, Member fetch join)
        final List<Store> stores = storeRepository.findAllWithinViewportWithPartner(wkt);
        if (stores.isEmpty()) {
            return List.of();
        }

        // 2) 해당 학생의 활성 UserPaper 조회 (paper, store, admin fetch join 포함)
        final List<UserPaper> userPapers = userPaperRepository.findActivePartnershipsByStudentId(memberId, LocalDate.now());
        if (userPapers.isEmpty()) {
            return List.of(); // active 제휴가 없으면 빈 리스트 반환
        }

        // 3) 뷰포트 매장 ID Set (O(1) 조회용)
        final Set<Long> storeIdSet = new HashSet<>();
        for (Store s : stores) storeIdSet.add(s.getId());

        // 4) UserPaper를 매장별로 그룹화: 뷰포트 내 매장 필터
        final Map<Long, List<Paper>> papersByStore = new LinkedHashMap<>();
        final Map<Long, Set<Long>> seenPapersByStore = new HashMap<>();
        for (UserPaper up : userPapers) {
            final Long storeId = up.getPaper().getStore().getId();
            if (!storeIdSet.contains(storeId)) continue;

            final Long paperId = up.getPaper().getId();
            Set<Long> seenPapers = seenPapersByStore.computeIfAbsent(storeId, k -> new HashSet<>());
            if (!seenPapers.add(paperId)) continue;

            papersByStore.computeIfAbsent(storeId, k -> new ArrayList<>()).add(up.getPaper());
        }

        // 5) active 제휴가 있는 매장만 필터링
        final List<Store> storesWithActivePaper = stores.stream()
                .filter(s -> papersByStore.containsKey(s.getId()))
                .toList();

        if (storesWithActivePaper.isEmpty()) {
            return List.of();
        }

        // 6) 선택된 paper ID 목록으로 각 paper의 최신 PaperContent 1건씩 일괄 조회
        final List<Long> selectedPaperIds = papersByStore.values().stream()
                .flatMap(List::stream)
                .map(Paper::getId)
                .toList();

        final Map<Long, PaperContent> contentByPaperId;
        if (selectedPaperIds.isEmpty()) {
            contentByPaperId = Collections.emptyMap();
        } else {
            contentByPaperId = paperContentRepository.findLatestByPaperIds(selectedPaperIds).stream()
                    .collect(Collectors.toMap(
                            pc -> pc.getPaper().getId(),
                            pc -> pc,
                            (a, b) -> a
                    ));
        }

        // 7) 매장별 DTO 생성
        return storesWithActivePaper.stream().map(s -> {
            final List<Paper> sPapers = papersByStore.get(s.getId());

            List<StoreMapResponseDTO.PartnershipInfo> partnerships = sPapers.stream()
                    .map(paper -> {
                        Long adminId = paper.getAdmin() != null ? paper.getAdmin().getId() : null;
                        String adminName = paper.getAdmin() != null ? paper.getAdmin().getName() : null;
                        String benefit = resolveBenefit(contentByPaperId.get(paper.getId()));
                        return new StoreMapResponseDTO.PartnershipInfo(adminId, adminName, benefit);
                    })
                    .filter(p -> p.adminId() != null)
                    .toList();

            return StoreMapResponseDTO.ofWithPartnerships(s, partnerships, amazonS3Manager);
        }).toList();
    }

    /** note가 있으면 note를, 없으면 generateBenefitText 결과를 반환 */
    private String resolveBenefit(PaperContent content) {
        if (content == null) return null;
        if (content.getNote() != null && !content.getNote().isBlank()) {
            return content.getNote();
        }
        return generateBenefitText(content);
    }

    private String generateBenefitText(PaperContent content) {
        if (content == null) return null;

        OptionType optionType = content.getOptionType();
        CriterionType criterionType = content.getCriterionType();

        if (optionType == OptionType.SERVICE) {
            if (criterionType == CriterionType.PRICE) {
                String cost = content.getCost() != null ? content.getCost().toString() : "-";
                String gift = content.getCategory() != null ? content.getCategory() : "상품";
                return cost + "원 이상 구매 시 " + gift + " 증정";
            } else if (criterionType == CriterionType.HEADCOUNT) {
                String people = content.getPeople() != null ? content.getPeople().toString() : "-";
                String gift = content.getCategory() != null ? content.getCategory() : "상품";
                return people + "명 이상 방문 시 " + gift + " 증정";
            }
            return "서비스 혜택";
        } else if (optionType == OptionType.DISCOUNT) {
            if (criterionType == CriterionType.PRICE) {
                String cost = content.getCost() != null ? content.getCost().toString() : "-";
                String rate = content.getDiscount() != null ? content.getDiscount().toString() : "-";
                return cost + "원 이상 구매 시 " + rate + "% 할인";
            } else if (criterionType == CriterionType.HEADCOUNT) {
                String people = content.getPeople() != null ? content.getPeople().toString() : "-";
                String rate = content.getDiscount() != null ? content.getDiscount().toString() : "-";
                return people + "명 이상 방문 시 " + rate + "% 할인";
            }
            return "할인 혜택";
        }
        return null;
    }

    @Override
    public List<StoreMapResponseDTO> searchStores(String keyword) {
        List<Store> stores = storeRepository.findByNameContainingIgnoreCaseOrderByIdDescWithPartner(keyword);

        if (stores.isEmpty()) {
            return List.of();
        }

        List<Long> storeIds = stores.stream().map(Store::getId).toList();

        // 매장별 모든 active Paper 조회
        List<Paper> papers = paperRepository.findByStoreIdIn(storeIds, ActivationStatus.ACTIVE);
        
        // active 제휴가 없는 매장 필터링
        Set<Long> storeIdsWithActivePaper = papers.stream()
                .map(p -> p.getStore().getId())
                .collect(Collectors.toSet());
        
        List<Store> storesWithActivePaper = stores.stream()
                .filter(s -> storeIdsWithActivePaper.contains(s.getId()))
                .toList();
        
        if (storesWithActivePaper.isEmpty()) {
            return List.of();
        }

        // 매장별 Paper 그룹화
        Map<Long, List<Paper>> papersByStore = papers.stream()
                .collect(Collectors.groupingBy(p -> p.getStore().getId()));

        // 모든 paper의 최신 PaperContent 조회
        List<Long> allPaperIds = papers.stream().map(Paper::getId).toList();
        Map<Long, PaperContent> contentByPaperId = allPaperIds.isEmpty() 
                ? Collections.emptyMap()
                : paperContentRepository.findLatestByPaperIds(allPaperIds).stream()
                        .collect(Collectors.toMap(
                                pc -> pc.getPaper().getId(),
                                pc -> pc,
                                (a, b) -> a
                        ));

        return storesWithActivePaper.stream().map(s -> {
            List<Paper> storePapers = papersByStore.getOrDefault(s.getId(), List.of());
            
            List<StoreMapResponseDTO.PartnershipInfo> partnerships = storePapers.stream()
                    .map(paper -> {
                        Long adminId = paper.getAdmin() != null ? paper.getAdmin().getId() : null;
                        String adminName = paper.getAdmin() != null ? paper.getAdmin().getName() : null;
                        PaperContent content = contentByPaperId.get(paper.getId());
                        String benefit = resolveBenefit(content);
                        return new StoreMapResponseDTO.PartnershipInfo(adminId, adminName, benefit);
                    })
                    .filter(p -> p.adminId() != null)
                    .toList();

            return StoreMapResponseDTO.ofWithPartnerships(s, partnerships, amazonS3Manager);
        }).toList();
    }

    @Override
    public List<PartnerMapResponseDTO> searchPartner(String keyword, Long memberId) {
        List<Partner> partners = partnerRepository.searchPartnerByKeywordWithMember(keyword);

        if (partners.isEmpty()) {
            return List.of();
        }

        List<Long> partnerIds = partners.stream().map(Partner::getId).toList();
        List<Paper> papers = paperRepository.findByAdminIdAndPartnerIdInAndIsActivated(memberId, partnerIds, ActivationStatus.ACTIVE);
        Map<Long, Paper> partnerIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getPartner().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        return partners.stream()
                .map(p -> PartnerMapResponseDTO.of(p, partnerIdToPaper.get(p.getId()), amazonS3Manager))
                .toList();
    }

    @Override
    public List<AdminMapResponseDTO> searchAdmin(String keyword, Long memberId) {
        List<Admin> admins = adminRepository.searchAdminByKeywordWithMember(keyword, PageRequest.of(0, 50));

        if (admins.isEmpty()) {
            return List.of();
        }

        List<Long> adminIds = admins.stream().map(Admin::getId).toList();
        List<Paper> papers = paperRepository.findByAdminIdInAndPartnerIdAndIsActivated(adminIds, memberId, ActivationStatus.ACTIVE);
        Map<Long, Paper> adminIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getAdmin().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        return admins.stream()
                .map(a -> AdminMapResponseDTO.of(a, adminIdToPaper.get(a.getId()), amazonS3Manager))
                .toList();
    }

    private String toWKT(MapRequestDTO v) {
        return String.format(
                "POLYGON((%f %f, %f %f, %f %f, %f %f, %f %f))",
                v.lng1(), v.lat1(),
                v.lng2(), v.lat2(),
                v.lng3(), v.lat3(),
                v.lng4(), v.lat4(),
                v.lng1(), v.lat1()
        );
    }
}
