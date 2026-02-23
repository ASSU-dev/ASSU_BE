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
     * 위 기준을 만족하는 paper 중, 매장당 중복 없이 가장 최근 2건(paper+papercontent 쌍)을 가져와
     * adminId1/2, adminName1/2, benefit1/2를 채운 StoreMapResponseDTO 반환.
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
            return stores.stream()
                    .map(s -> StoreMapResponseDTO.of(s, null, null, null, null, null, null, amazonS3Manager))
                    .toList();
        }

        // 3) 뷰포트 매장 ID Set (O(1) 조회용)
        final Set<Long> storeIdSet = new HashSet<>();
        for (Store s : stores) storeIdSet.add(s.getId());

        // 4) UserPaper를 매장별로 그룹화: 뷰포트 내 매장 필터 + paper 중복 제거 + 매장당 최대 2건
        //    findActivePartnershipsByStudentId 결과는 이미 paper.id DESC 정렬 상태
        final Map<Long, List<Paper>> papersByStore = new LinkedHashMap<>();
        final Set<Long> seenPaperIds = new HashSet<>();
        for (UserPaper up : userPapers) {
            final Long storeId = up.getPaper().getStore().getId();
            if (!storeIdSet.contains(storeId)) continue;

            final Long paperId = up.getPaper().getId();
            if (!seenPaperIds.add(paperId)) continue; // 이미 처리한 paper면 스킵

            final List<Paper> list = papersByStore.computeIfAbsent(storeId, k -> new ArrayList<>(2));
            if (list.size() < 2) {
                list.add(up.getPaper());
            }
        }

        // 5) 선택된 paper ID 목록으로 각 paper의 최신 PaperContent 1건씩 일괄 조회
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

        // 6) 매장별 DTO 생성
        return stores.stream().map(s -> {
            final List<Paper> sPapers = papersByStore.getOrDefault(s.getId(), Collections.emptyList());

            Long adminId1 = null; String adminName1 = null; String benefit1 = null;
            Long adminId2 = null; String adminName2 = null; String benefit2 = null;

            if (!sPapers.isEmpty()) {
                final Paper p1 = sPapers.get(0);
                if (p1.getAdmin() != null) {
                    adminId1 = p1.getAdmin().getId();
                    adminName1 = p1.getAdmin().getName();
                }
                benefit1 = resolveBenefit(contentByPaperId.get(p1.getId()));
            }
            if (sPapers.size() > 1) {
                final Paper p2 = sPapers.get(1);
                if (p2.getAdmin() != null) {
                    adminId2 = p2.getAdmin().getId();
                    adminName2 = p2.getAdmin().getName();
                }
                benefit2 = resolveBenefit(contentByPaperId.get(p2.getId()));
            }

            return StoreMapResponseDTO.of(s, adminId1, adminId2, adminName1, adminName2, benefit1, benefit2, amazonS3Manager);
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

        // 매장당 최신 Paper 1건 (admin 정보용)
        List<Paper> papers = paperRepository.findByStoreIdIn(storeIds);
        Map<Long, Paper> storeIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getStore().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        List<Long> adminIds = papers.stream()
                .filter(p -> p.getAdmin() != null)
                .map(p -> p.getAdmin().getId())
                .distinct()
                .toList();
        List<Admin> admins = adminIds.isEmpty() ? List.of() : adminRepository.findAllById(adminIds);
        Map<Long, Admin> adminIdToAdmin = admins.stream()
                .collect(Collectors.toMap(Admin::getId, a -> a));

        // 매장당 최신 PaperContent 1건 (benefit 생성용)
        List<PaperContent> contents = paperContentRepository.findTopByStoreIdIn(storeIds);
        Map<Long, PaperContent> storeIdToContent = contents.stream()
                .collect(Collectors.toMap(
                        pc -> pc.getPaper().getStore().getId(),
                        pc -> pc,
                        (pc1, pc2) -> pc1.getId() > pc2.getId() ? pc1 : pc2
                ));

        return stores.stream().map(s -> {
            PaperContent content = storeIdToContent.get(s.getId());
            Paper paper = storeIdToPaper.get(s.getId());
            Long adminId = paper != null && paper.getAdmin() != null ? paper.getAdmin().getId() : null;
            Admin admin = adminId != null ? adminIdToAdmin.get(adminId) : null;

            return StoreMapResponseDTO.of(
                    s,
                    adminId, null,
                    admin != null ? admin.getName() : null, null,
                    generateBenefitText(content), null,
                    amazonS3Manager
            );
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
