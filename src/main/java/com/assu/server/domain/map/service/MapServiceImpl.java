package com.assu.server.domain.map.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.map.dto.MapRequestDTO;
import com.assu.server.domain.map.dto.MapResponseDTO;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
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
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.config.KakaoLocalClient;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;

import com.assu.server.infra.s3.AmazonS3Manager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.auth.scheme.internal.S3EndpointResolverAware;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MapServiceImpl implements MapService {

    private final AdminRepository adminRepository;
    private final PartnerRepository partnerRepository;
    private final StoreRepository storeRepository;
    private final PaperContentRepository paperContentRepository;
    private final PaperRepository paperRepository;
    private final GeometryFactory geometryFactory;
    private final GoodsRepository goodsRepository;
    private final AmazonS3Manager amazonS3Manager;

    @Override
    public List<MapResponseDTO.PartnerMapResponseDTO> getPartners(MapRequestDTO.ViewOnMapDTO viewport, Long memberId) {

        String wkt = toWKT(viewport);
        List<Partner> partners = partnerRepository.findAllWithinViewport(wkt);

        return partners.stream().map(p -> {
            Paper active = paperRepository.findTopByAdmin_IdAndPartner_IdAndIsActivatedOrderByIdDesc(memberId, p.getId(), ActivationStatus.ACTIVE)
                    .orElse(null);

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
    public List<MapResponseDTO.AdminMapResponseDTO> getAdmins(MapRequestDTO.ViewOnMapDTO viewport, Long memberId) {
        String wkt = toWKT(viewport);
        List<Admin> admins = adminRepository.findAllWithinViewport(wkt);

        return admins.stream().map(a -> {
            Paper active = paperRepository.findTopByAdmin_IdAndPartner_IdAndIsActivatedOrderByIdDesc(a.getId(), memberId, ActivationStatus.ACTIVE)
                    .orElse(null);

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
    public List<MapResponseDTO.StoreMapResponseDTO> getStores(MapRequestDTO.ViewOnMapDTO viewport, Long memberId) {
        final String wkt = toWKT(viewport);

        // 1) 뷰포트 내 매장 조회
        final List<Store> stores = storeRepository.findAllWithinViewport(wkt);

        // 2) 매장별 content는 "있으면 사용, 없으면 null" 전략
        return stores.stream().map(s -> {
            final boolean hasPartner = (s.getPartner() != null);

            // 2-1) 유효한 paper_content만 조회 (없으면 null 허용)
            final PaperContent content = paperContentRepository.findLatestValidByStoreIdNative(
                    s.getId(),
                    ActivationStatus.ACTIVE.name(),
                    OptionType.SERVICE.name(),
                    OptionType.DISCOUNT.name(),
                    CriterionType.PRICE.name(),
                    CriterionType.HEADCOUNT.name()
            ).orElse(null);

            // 2-2) admin 정보 (null-safe)
            final Long adminId = paperRepository.findTopPaperByStoreId(s.getId())
                    .map(p -> p.getAdmin() != null ? p.getAdmin().getId() : null)
                    .orElse(null);

            String adminName = null;
            if (adminId != null) {
                final Admin admin = adminRepository.findById(adminId).orElse(null);
                adminName = (admin != null ? admin.getName() : null);
            }

            // 2-3) S3 presigned URL (키가 없으면 null)
            final String key = (s.getPartner() != null && s.getPartner().getMember() != null) ? s.getPartner().getMember().getProfileUrl() : null;
            final String profileUrl = (key != null && !key.isBlank()) ? amazonS3Manager.generatePresignedUrl(key) : null;

            // phoneNumber null-safe 처리 (빈 문자열로 변환)
            final String phoneNumber = (s.getPartner() != null
                    && s.getPartner().getMember() != null
                    && s.getPartner().getMember().getPhoneNum() != null)
                    ? s.getPartner().getMember().getPhoneNum()
                    : "";

            // 2-4) DTO 빌드 (content null 허용)
            return MapResponseDTO.StoreMapResponseDTO.builder()
                    .storeId(s.getId())
                    .adminId(adminId)
                    .adminName(adminName)
                    .name(s.getName())
                    .address(s.getAddress() != null ? s.getAddress() : s.getDetailAddress())
                    .rate(s.getRate())
                    .criterionType(content != null ? content.getCriterionType() : null)
                    .optionType(content != null ? content.getOptionType() : null)
                    .people(content != null ? content.getPeople() : null)
                    .cost(content != null ? content.getCost() : null)
                    .category(content != null ? content.getCategory() : null)
                    .discountRate(content != null ? content.getDiscount() : null)
                    .hasPartner(hasPartner)
                    .latitude(s.getLatitude())
                    .longitude(s.getLongitude())
                    .profileUrl(profileUrl)
                    .phoneNumber(phoneNumber)
                    .build();
        }).toList();
    }

    @Override
    public List<MapResponseDTO.StoreMapResponseDTO> searchStores(String keyword) {
        List<Store> stores = storeRepository.findByNameContainingIgnoreCaseOrderByIdDesc(keyword);

        return stores.stream().map(s -> {
            boolean hasPartner = s.getPartner() != null;
            PaperContent content = paperContentRepository.findTopByPaperStoreIdOrderByIdDesc(s.getId())
                    .orElse(null);

            final String key = (s.getPartner() != null && s.getPartner().getMember() != null) ? s.getPartner().getMember().getProfileUrl() : null;
            final String profileUrl = (key != null && !key.isBlank()) ? amazonS3Manager.generatePresignedUrl(key) : null;

            Long adminId = paperRepository.findTopPaperByStoreId(s.getId())
                    .map(p -> p.getAdmin() != null ? p.getAdmin().getId() : null)
                    .orElse(null);

            Admin admin = adminRepository.findById(adminId).orElse(null);

            String finalCategory = null;

            if (content != null) {
                // 2. content에 카테고리가 이미 존재하면 그 값을 사용합니다.
                if (content.getCategory() != null) {
                    finalCategory = content.getCategory();
                }
                // 3. 카테고리가 없고, 옵션 타입이 SERVICE인 경우 Goods를 조회합니다.
                else if (content.getOptionType() == OptionType.SERVICE) {
                    List<Goods> goods = goodsRepository.findByContentId(content.getId());

                    // 4. (가장 중요) goods 리스트가 비어있지 않은지 반드시 확인합니다.
                    if (!goods.isEmpty()) {
                        finalCategory = goods.get(0).getBelonging();
                    }
                    // goods가 비어있으면 finalCategory는 그대로 null로 유지됩니다.
                }
            }

            // phoneNumber null-safe 처리 (빈 문자열로 변환)
            String phoneNumber = (s.getPartner() != null
                    && s.getPartner().getMember() != null
                    && s.getPartner().getMember().getPhoneNum() != null)
                    ? s.getPartner().getMember().getPhoneNum()
                    : "";

            return MapResponseDTO.StoreMapResponseDTO.builder()
                    .storeId(s.getId())
                    .adminName(admin != null ? admin.getName() : null)
                    .adminId(adminId)
                    .name(s.getName())
                    .note(content.getNote())
                    .address(s.getAddress() != null ? s.getAddress() : s.getDetailAddress())
                    .rate(s.getRate())
                    .criterionType(content != null ? content.getCriterionType() : null)
                    .optionType(content != null ? content.getOptionType() : null)
                    .people(content != null ? content.getPeople() : null)
                    .cost(content != null ? content.getCost() : null)
                    .category(finalCategory)
                    .discountRate(content != null ? content.getDiscount() : null)
                    .hasPartner(hasPartner)
                    .latitude(s.getLatitude())
                    .longitude(s.getLongitude())
                    .profileUrl(profileUrl)
                    .phoneNumber(phoneNumber)
                    .build();
        }).toList();
    }

    @Override
    public List<MapResponseDTO.PartnerMapResponseDTO> searchPartner(String keyword, Long memberId) {
        List<Partner> partners = partnerRepository.searchPartnerByKeyword(keyword);

        return partners.stream().map(p -> {
                Paper active = paperRepository
                                    .findTopByAdmin_IdAndPartner_IdAndIsActivatedOrderByIdDesc(memberId, p.getId(), ActivationStatus.ACTIVE)
                                    .orElse(null);

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
    public List<MapResponseDTO.AdminMapResponseDTO> searchAdmin(String keyword, Long memberId) {
        List<Admin> admins = adminRepository.searchAdminByKeyword(keyword);

        return admins.stream().map(a -> {
            Paper active = paperRepository
                    .findTopByAdmin_IdAndPartner_IdAndIsActivatedOrderByIdDesc(a.getId(), memberId, ActivationStatus.ACTIVE)
                    .orElse(null);

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

    private String toWKT(MapRequestDTO.ViewOnMapDTO v) {
        return String.format(
                "POLYGON((%f %f, %f %f, %f %f, %f %f, %f %f))",
                v.getLng1(), v.getLat1(),
                v.getLng2(), v.getLat2(),
                v.getLng3(), v.getLat3(),
                v.getLng4(), v.getLat4(),
                v.getLng1(), v.getLat1()
        );
    }

    private Point toPoint(Double lng, Double lat) {
        if (lng == null || lat == null) return null;
        Point p = geometryFactory.createPoint(new Coordinate(lng, lat));
        p.setSRID(4326);
        return p;
    }

    private String pickDisplayAddress(String road, String jibun) {
        return (road != null && !road.isBlank()) ? road : jibun;
    }
}
