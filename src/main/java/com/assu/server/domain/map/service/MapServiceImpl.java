package com.assu.server.domain.map.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.map.dto.AdminMapResponseDTO;
import com.assu.server.domain.map.dto.MapRequestDTO;
import com.assu.server.domain.map.dto.PartnerMapResponseDTO;
import com.assu.server.domain.map.dto.StoreMapResponseDTO;
import com.assu.server.domain.map.dto.StoreMapResponseV2DTO;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import com.assu.server.domain.partnership.repository.GoodsRepository;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.user.entity.UserPaper;
import com.assu.server.domain.user.repository.UserPaperRepository;
import com.assu.server.infra.s3.AmazonS3Manager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
    private final GoodsRepository goodsRepository;
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
        List<Admin> admins = adminRepository.findAllWithinViewportWithMember(wkt);

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

    @Override
    public List<StoreMapResponseDTO> getStores(MapRequestDTO viewport, Long memberId) {
        final String wkt = toWKT(viewport);

        // 1) 뷰포트 내 매장 조회 (Partner, Member fetch join)
        final List<Store> stores = storeRepository.findAllWithinViewportWithPartner(wkt);

        if (stores.isEmpty()) {
            return List.of();
        }

        // 2) Paper 및 Admin 정보 batch 조회
        List<Long> storeIds = stores.stream().map(Store::getId).toList();
        List<Paper> papers = paperRepository.findByStoreIdIn(storeIds);
        Map<Long, Paper> storeIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getStore().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        List<Long> adminIds = papers.stream()
                .map(p -> p.getAdmin() != null ? p.getAdmin().getId() : null)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<Admin> admins = adminIds.isEmpty() ? List.of() : adminRepository.findAllById(adminIds);
        Map<Long, Admin> adminIdToAdmin = admins.stream()
                .collect(Collectors.toMap(Admin::getId, a -> a));

        // 3) PaperContent batch 조회 (N+1 방지)
        List<PaperContent> contents = paperContentRepository.findLatestValidByStoreIdInNative(
                storeIds,
                ActivationStatus.ACTIVE.name(),
                OptionType.SERVICE.name(),
                OptionType.DISCOUNT.name(),
                CriterionType.PRICE.name(),
                CriterionType.HEADCOUNT.name()
        );
        Map<Long, PaperContent> storeIdToContent = contents.stream()
                .collect(Collectors.toMap(
                        pc -> pc.getPaper().getStore().getId(),
                        pc -> pc,
                        (pc1, pc2) -> pc1.getId() > pc2.getId() ? pc1 : pc2
                ));

        // 4) 매장별 DTO 생성
        return stores.stream().map(s -> {
            final PaperContent content = storeIdToContent.get(s.getId());
            final Paper paper = storeIdToPaper.get(s.getId());
            final Long adminId = paper != null && paper.getAdmin() != null ? paper.getAdmin().getId() : null;
            final String adminName = adminId != null ? adminIdToAdmin.getOrDefault(adminId, null) != null
                    ? adminIdToAdmin.get(adminId).getName() : null : null;

            return StoreMapResponseDTO.of(s, content, adminId, adminName, amazonS3Manager);
        }).toList();
    }

    @Override
    public List<StoreMapResponseV2DTO> getStoresV2(MapRequestDTO viewport, Long memberId) {
        final String wkt = toWKT(viewport);
        final List<Store> stores = storeRepository.findAllWithinViewport(wkt);
        final List<UserPaper> userPapers = userPaperRepository.findActivePartnershipsByStudentId(memberId, java.time.LocalDate.now());

        // 모든 매장 ID 수집
        List<Long> storeIds = stores.stream().map(Store::getId).toList();

        // 빈 화면(매장 없음) 방어 코드
        if (storeIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // 사용자가 가진 Paper ID 수집 (쿼리에 파라미터로 넘기기 위해 위치를 위로 올림!)
        List<Long> userPaperIds = userPapers.stream()
                .map(up -> up.getPaper().getId())
                .toList();

        // 일괄 조회 1: 모든 매장의 PaperContent (내 혜택 기준 매장당 최대 2개)
        List<PaperContent> allContents;
        if (userPaperIds.isEmpty()) {
            // 방어 코드: 사용자가 가진 혜택이 없다면 IN () 에러 방지를 위해 쿼리 실행 없이 빈 리스트 할당
            allContents = java.util.Collections.emptyList();
        } else {
            allContents = paperContentRepository.findLatestValidByStoreIdInNativeMax2(
                    storeIds,
                    ActivationStatus.ACTIVE.name(),
                    OptionType.SERVICE.name(),
                    OptionType.DISCOUNT.name(),
                    CriterionType.PRICE.name(),
                    CriterionType.HEADCOUNT.name(),
                    userPaperIds // ⭐️ 새로 추가된 파라미터 전달!
            );
        }

        // 일괄 조회 2: 모든 매장의 Paper와 Admin 정보 (Fetch Join)
        List<Paper> allPapers = paperRepository.findLatestPapersByStoreIds(storeIds);

        // 매장별 Paper 매핑 (매장당 최신 1개)
        java.util.Map<Long, Paper> paperByStore = allPapers.stream()
                .collect(java.util.stream.Collectors.toMap(
                        p -> p.getStore().getId(),
                        p -> p,
                        (existing, replacement) -> existing // 이미 키가 있으면 기존 값(최신) 유지
                ));

        // 매장별로 PaperContent 그룹화
        java.util.Map<Long, List<PaperContent>> contentsByStore = allContents.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        pc -> pc.getPaper().getStore().getId()
                ));

        return stores.stream().map(s -> {
            final boolean hasPartner = (s.getPartner() != null);

            // ⭐️ DB에서 이미 '내 혜택 중 상위 2개'만 가져왔으므로 filter와 limit이 필요 없음! 꺼내기만 하면 끝.
            List<PaperContent> benefits = contentsByStore.getOrDefault(s.getId(), java.util.Collections.emptyList());

            // 혜택 텍스트 생성
            String partner1 = null;
            String benefit1 = null;
            String partner2 = null;
            String benefit2 = null;

            if (!benefits.isEmpty()) {
                PaperContent content1 = benefits.get(0);
                partner1 = content1.getPaper() != null && content1.getPaper().getPartner() != null
                        ? content1.getPaper().getPartner().getName() : null;
                benefit1 = generateBenefitText(content1);
            }

            if (benefits.size() > 1) {
                PaperContent content2 = benefits.get(1);
                partner2 = content2.getPaper() != null && content2.getPaper().getPartner() != null
                        ? content2.getPaper().getPartner().getName() : null;
                benefit2 = generateBenefitText(content2);
            }

            Paper paper = paperByStore.get(s.getId());
            Long adminId = null;
            String adminName = null;
            if (paper != null && paper.getAdmin() != null) {
                adminId = paper.getAdmin().getId();
                String name = paper.getAdmin().getName();
                adminName = (name != null) ? name : ""; // Null이면 빈 문자열로 덮어쓰기!
            }

            // S3 presigned URL
            final String key = (s.getPartner() != null && s.getPartner().getMember() != null)
                    ? s.getPartner().getMember().getProfileUrl() : null;
            final String profileUrl = (key != null && !key.isBlank())
                    ? amazonS3Manager.generatePresignedUrl(key) : null;

            // phoneNumber
            final String phoneNumber = (s.getPartner() != null
                    && s.getPartner().getMember() != null
                    && s.getPartner().getMember().getPhoneNum() != null)
                    ? s.getPartner().getMember().getPhoneNum()
                    : "";

            return new StoreMapResponseV2DTO(
                    s.getId(), adminId, adminName, s.getName(),
                    s.getAddress() != null ? s.getAddress() : s.getDetailAddress(),
                    s.getRate(), hasPartner,
                    s.getLatitude(), s.getLongitude(),
                    profileUrl, phoneNumber,
                    partner1, partner2, benefit1, benefit2
            );
        }).toList();
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
        List<Paper> papers = paperRepository.findByStoreIdIn(storeIds);
        Map<Long, Paper> storeIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getStore().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        List<Long> adminIds = papers.stream()
                .map(p -> p.getAdmin() != null ? p.getAdmin().getId() : null)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<Admin> admins = adminIds.isEmpty() ? List.of() : adminRepository.findAllById(adminIds);
        Map<Long, Admin> adminIdToAdmin = admins.stream()
                .collect(Collectors.toMap(Admin::getId, a -> a));

        // PaperContent batch 조회 (N+1 방지)
        List<PaperContent> contents = paperContentRepository.findTopByStoreIdIn(storeIds);
        Map<Long, PaperContent> storeIdToContent = contents.stream()
                .collect(Collectors.toMap(
                        pc -> pc.getPaper().getStore().getId(),
                        pc -> pc,
                        (pc1, pc2) -> pc1.getId() > pc2.getId() ? pc1 : pc2
                ));

        // Goods batch 조회 (N+1 방지)
        List<Long> contentIds = contents.stream().map(PaperContent::getId).toList();
        List<Goods> allGoods = contentIds.isEmpty() ? List.of() : goodsRepository.findByContentIdIn(contentIds);
        Map<Long, List<Goods>> contentIdToGoods = allGoods.stream()
                .collect(Collectors.groupingBy(g -> g.getContent().getId()));

        return stores.stream().map(s -> {
            PaperContent content = storeIdToContent.get(s.getId());
            Paper paper = storeIdToPaper.get(s.getId());
            Long adminId = paper != null && paper.getAdmin() != null ? paper.getAdmin().getId() : null;
            Admin admin = adminId != null ? adminIdToAdmin.get(adminId) : null;

            String finalCategory = null;

            if (content != null) {
                if (content.getCategory() != null) {
                    finalCategory = content.getCategory();
                }
                else if (content.getOptionType() == OptionType.SERVICE) {
                    List<Goods> goods = contentIdToGoods.getOrDefault(content.getId(), List.of());

                    if (!goods.isEmpty()) {
                        finalCategory = goods.get(0).getBelonging();
                    }
                }
            }

            return StoreMapResponseDTO.ofSearch(
                    s, content, finalCategory, adminId,
                    admin != null ? admin.getName() : null,
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
        List<Admin> admins = adminRepository.searchAdminByKeywordWithMember(keyword);

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
