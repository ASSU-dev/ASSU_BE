package com.assu.server.domain.map.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.map.converter.MapConverter;
import com.assu.server.domain.map.dto.MapRequestDTO;
import com.assu.server.domain.map.dto.MapResponseDTO;
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

    @Override
    public List<MapResponseDTO.PartnerMapResponseDTO> getPartners(MapRequestDTO viewport, Long memberId) {

        String wkt = toWKT(viewport);
        List<Partner> partners = partnerRepository.findAllWithinViewportWithMember(wkt);

        if (partners.isEmpty()) {
            return List.of();
        }

        List<Long> partnerIds = partners.stream().map(Partner::getId).toList();
        List<Paper> papers = paperRepository.findByAdminIdAndPartnerIdInAndIsActivated(memberId, partnerIds, ActivationStatus.ACTIVE);
        Map<Long, Paper> partnerIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getPartner().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        return partners.stream().map(p -> {
            Paper active = partnerIdToPaper.get(p.getId());

            final String key = (p.getMember() != null) ? p.getMember().getProfileUrl() : null;
            final String profileUrl = (key != null && !key.isBlank()) ? amazonS3Manager.generatePresignedUrl(key) : null;

            return MapResponseDTO.PartnerMapResponseDTO.builder()
                    .partnerId(p.getId())
                    .name(p.getName())
                    .address(p.getAddress() != null ? p.getAddress() : p.getDetailAddress())
                    .isPartnered(active != null)
                    .partnershipId(active != null ? active.getId() : null)
                    .partnershipStartDate(active != null ? active.getPartnershipPeriodStart() : null)
                    .partnershipEndDate(active != null ? active.getPartnershipPeriodEnd() : null)
                    .latitude(p.getLatitude())
                    .longitude(p.getLongitude())
                    .profileUrl(profileUrl)
                    .phoneNumber(p.getMember().getPhoneNum())
                    .build();
        }).toList();
    }

    @Override
    public List<MapResponseDTO.AdminMapResponseDTO> getAdmins(MapRequestDTO viewport, Long memberId) {
        String wkt = toWKT(viewport);
        List<Admin> admins = adminRepository.findAllWithinViewportWithMember(wkt);

        if (admins.isEmpty()) {
            return List.of();
        }

        List<Long> adminIds = admins.stream().map(Admin::getId).toList();
        List<Paper> papers = paperRepository.findByAdminIdInAndPartnerIdAndIsActivated(adminIds, memberId, ActivationStatus.ACTIVE);
        Map<Long, Paper> adminIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getAdmin().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        return admins.stream().map(a -> {
            Paper active = adminIdToPaper.get(a.getId());

            final String key = (a.getMember() != null) ? a.getMember().getProfileUrl() : null;
            final String profileUrl = (key != null && !key.isBlank()) ? amazonS3Manager.generatePresignedUrl(key) : null;

            return MapResponseDTO.AdminMapResponseDTO.builder()
                    .adminId(a.getId())
                    .name(a.getName())
                    .address(a.getOfficeAddress() != null ? a.getOfficeAddress() : a.getDetailAddress())
                    .isPartnered(active != null)
                    .partnershipId(active != null ? active.getId() : null)
                    .partnershipStartDate(active != null ? active.getPartnershipPeriodStart() : null)
                    .partnershipEndDate(active != null ? active.getPartnershipPeriodEnd() : null)
                    .latitude(a.getLatitude())
                    .longitude(a.getLongitude())
                    .profileUrl(profileUrl)
                    .phoneNumber(a.getMember().getPhoneNum())
                    .build();
        }).toList();
    }

    @Override
    public List<MapResponseDTO.StoreMapResponseDTO> getStores(MapRequestDTO viewport, Long memberId) {
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

            return MapConverter.toStoreMapResponseDTO(s, content, adminId, adminName, amazonS3Manager);
        }).toList();
    }

    @Override
    public List<MapResponseDTO.StoreMapResponseDTO> searchStores(String keyword) {
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

            return MapConverter.toStoreMapResponseDTOForSearch(
                    s, content, finalCategory, adminId,
                    admin != null ? admin.getName() : null,
                    amazonS3Manager
            );
        }).toList();
    }

    @Override
    public List<MapResponseDTO.PartnerMapResponseDTO> searchPartner(String keyword, Long memberId) {
        List<Partner> partners = partnerRepository.searchPartnerByKeywordWithMember(keyword);

        if (partners.isEmpty()) {
            return List.of();
        }

        List<Long> partnerIds = partners.stream().map(Partner::getId).toList();
        List<Paper> papers = paperRepository.findByAdminIdAndPartnerIdInAndIsActivated(memberId, partnerIds, ActivationStatus.ACTIVE);
        Map<Long, Paper> partnerIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getPartner().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        return partners.stream().map(p -> {
            Paper active = partnerIdToPaper.get(p.getId());
            return MapConverter.toPartnerMapResponseDTO(p, active, amazonS3Manager);
        }).toList();
    }

    @Override
    public List<MapResponseDTO.AdminMapResponseDTO> searchAdmin(String keyword, Long memberId) {
        List<Admin> admins = adminRepository.searchAdminByKeywordWithMember(keyword);

        if (admins.isEmpty()) {
            return List.of();
        }

        List<Long> adminIds = admins.stream().map(Admin::getId).toList();
        List<Paper> papers = paperRepository.findByAdminIdInAndPartnerIdAndIsActivated(adminIds, memberId, ActivationStatus.ACTIVE);
        Map<Long, Paper> adminIdToPaper = papers.stream()
                .collect(Collectors.toMap(p -> p.getAdmin().getId(), p -> p, (p1, p2) -> p1.getId() > p2.getId() ? p1 : p2));

        return admins.stream().map(a -> {
            Paper active = adminIdToPaper.get(a.getId());
            return MapConverter.toAdminMapResponseDTO(a, active, amazonS3Manager);
        }).toList();
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
