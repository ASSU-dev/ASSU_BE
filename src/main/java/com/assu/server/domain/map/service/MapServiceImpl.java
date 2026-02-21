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
import com.assu.server.domain.user.entity.UserPaper;
import com.assu.server.domain.user.repository.UserPaperRepository;
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

import static reactor.core.publisher.Mono.when;

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
    private final UserPaperRepository userPaperRepository;

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
    public List<MapResponseDTO.StoreMapResponseV2DTO> getStoresV2(MapRequestDTO.ViewOnMapDTO viewport, Long memberId) {
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

            return MapResponseDTO.StoreMapResponseV2DTO.builder()
                    .storeId(s.getId())
                    .adminId(adminId)
                    .adminName(adminName)
                    .name(s.getName())
                    .address(s.getAddress() != null ? s.getAddress() : s.getDetailAddress())
                    .rate(s.getRate())
                    .hasPartner(hasPartner)
                    .latitude(s.getLatitude())
                    .longitude(s.getLongitude())
                    .profileUrl(profileUrl)
                    .phoneNumber(phoneNumber)
                    .partner1(partner1)
                    .benefit1(benefit1)
                    .partner2(partner2)
                    .benefit2(benefit2)
                    .build();
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
