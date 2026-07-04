package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.service.WithdrawalService;
import com.assu.server.domain.backoffice.dto.BackofficeDocumentUrlResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeMemberDetailDTO;
import com.assu.server.domain.backoffice.dto.BackofficeMemberSummaryDTO;
import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.s3.AmazonS3Manager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BackofficeMemberServiceImpl implements BackofficeMemberService {

    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final WithdrawalService withdrawalService;
    private final AmazonS3Manager amazonS3Manager;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<BackofficeMemberSummaryDTO> listMembers(
            UserRole role,
            ActivationStatus status,
            Boolean deleted,
            Pageable pageable
    ) {
        Page<Member> page = memberRepository.searchMemberSummaries(role, status, deleted, pageable);
        Map<Long, Member> membersWithProfiles = loadProfilesForSummaries(page.getContent());
        return PageResponseDTO.of(page.map(member -> BackofficeMemberSummaryDTO.from(
                membersWithProfiles.getOrDefault(member.getId(), member)
        )));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<BackofficeMemberSummaryDTO> listDeletedMembers(Pageable pageable) {
        return listMembers(null, null, true, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public BackofficeMemberDetailDTO getMemberDetail(Long memberId) {
        Member member = findMemberWithRoleProfile(memberId);
        return toMemberDetail(member);
    }

    @Override
    public BackofficeMemberSummaryDTO approveMember(Long memberId) {
        Member member = findMemberWithRoleProfile(memberId);
        assertApprovalTarget(member);
        assertPendingApproval(member);

        member.setIsActivated(ActivationStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();

        if (member.getRole() == UserRole.PARTNER) {
            Partner partner = member.getPartnerProfile();
            partner.setIsLicenseVerified(true);
            partner.setLicenseVerifiedAt(now);
            activatePartnerStore(partner);
        } else {
            Admin admin = member.getAdminProfile();
            admin.setIsSignVerified(true);
            admin.setSignVerifiedAt(now);
        }

        return BackofficeMemberSummaryDTO.from(member);
    }

    @Override
    public BackofficeMemberSummaryDTO rejectMember(Long memberId) {
        Member member = findMemberWithRoleProfile(memberId);
        assertApprovalTarget(member);
        assertPendingApproval(member);

        member.setIsActivated(ActivationStatus.INACTIVE);
        return BackofficeMemberSummaryDTO.from(member);
    }

    @Override
    public void forceWithdrawMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
        if (member.getRole() == UserRole.BACKOFFICE) {
            throw new CustomAuthException(ErrorStatus.CANNOT_WITHDRAW_BACKOFFICE_MEMBER);
        }
        withdrawalService.withdrawMember(memberId);
    }

    @Override
    public BackofficeMemberSummaryDTO restoreMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
        assertNotBackofficeOperator(member);

        if (member.getDeletedAt() == null) {
            throw new CustomAuthException(ErrorStatus.MEMBER_NOT_DELETED);
        }

        member.setDeletedAt(null);
        return BackofficeMemberSummaryDTO.from(loadProfileForSummary(member));
    }

    @Override
    @Transactional(readOnly = true)
    public BackofficeDocumentUrlResponseDTO getPartnerLicenseUrl(Long memberId) {
        Partner partner = findPartner(memberId);
        if (partner.getLicenseUrl() == null || partner.getLicenseUrl().isBlank()) {
            throw new CustomAuthException(ErrorStatus.LICENSE_NOT_FOUND);
        }
        String url = amazonS3Manager.generatePresignedUrl(partner.getLicenseUrl());
        if (url == null || url.isBlank()) {
            throw new CustomAuthException(ErrorStatus.LICENSE_NOT_FOUND);
        }
        return BackofficeDocumentUrlResponseDTO.of(url);
    }

    @Override
    public BackofficeMemberSummaryDTO verifyPartnerLicense(Long memberId) {
        Member member = findMemberWithRoleProfile(memberId);
        Partner partner = findPartnerProfile(member);

        if (partner.getLicenseUrl() == null || partner.getLicenseUrl().isBlank()) {
            throw new CustomAuthException(ErrorStatus.LICENSE_NOT_FOUND);
        }

        partner.setIsLicenseVerified(true);
        partner.setLicenseVerifiedAt(LocalDateTime.now());
        return BackofficeMemberSummaryDTO.from(member);
    }

    @Override
    @Transactional(readOnly = true)
    public BackofficeDocumentUrlResponseDTO getAdminSignImageUrl(Long memberId) {
        Admin admin = findAdmin(memberId);
        if (admin.getSignImageUrl() == null || admin.getSignImageUrl().isBlank()) {
            throw new CustomAuthException(ErrorStatus.SIGN_IMAGE_NOT_FOUND);
        }
        String url = amazonS3Manager.generatePresignedUrl(admin.getSignImageUrl());
        if (url == null || url.isBlank()) {
            throw new CustomAuthException(ErrorStatus.SIGN_IMAGE_NOT_FOUND);
        }
        return BackofficeDocumentUrlResponseDTO.of(url);
    }

    private Member findMemberWithRoleProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
        assertNotBackofficeOperator(member);

        return switch (member.getRole()) {
            case STUDENT -> memberRepository.findStudentWithProfileById(memberId)
                    .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
            case ADMIN -> memberRepository.findAdminWithProfileById(memberId)
                    .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
            case PARTNER -> memberRepository.findPartnerWithProfileById(memberId)
                    .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
            case BACKOFFICE -> throw new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER);
        };
    }

    private Map<Long, Member> loadProfilesForSummaries(List<Member> members) {
        if (members.isEmpty()) {
            return Map.of();
        }

        Map<Long, Member> enriched = new HashMap<>();

        List<Long> studentIds = members.stream()
                .filter(member -> member.getRole() == UserRole.STUDENT)
                .map(Member::getId)
                .toList();
        if (!studentIds.isEmpty()) {
            putAllById(enriched, memberRepository.findStudentsWithProfileByIdIn(studentIds));
        }

        List<Long> adminIds = members.stream()
                .filter(member -> member.getRole() == UserRole.ADMIN)
                .map(Member::getId)
                .toList();
        if (!adminIds.isEmpty()) {
            putAllById(enriched, memberRepository.findAdminsWithProfileByIdIn(adminIds));
        }

        List<Long> partnerIds = members.stream()
                .filter(member -> member.getRole() == UserRole.PARTNER)
                .map(Member::getId)
                .toList();
        if (!partnerIds.isEmpty()) {
            putAllById(enriched, memberRepository.findPartnersWithProfileByIdIn(partnerIds));
        }

        return enriched;
    }

    private Member loadProfileForSummary(Member member) {
        return loadProfilesForSummaries(List.of(member)).getOrDefault(member.getId(), member);
    }

    private void putAllById(Map<Long, Member> target, List<Member> members) {
        target.putAll(members.stream().collect(Collectors.toMap(Member::getId, Function.identity())));
    }

    private BackofficeMemberDetailDTO toMemberDetail(Member member) {
        return switch (member.getRole()) {
            case STUDENT -> BackofficeMemberDetailDTO.fromStudent(member);
            case ADMIN -> BackofficeMemberDetailDTO.fromAdmin(member);
            case PARTNER -> BackofficeMemberDetailDTO.fromPartner(member);
            case BACKOFFICE -> throw new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER);
        };
    }

    private Partner findPartner(Long memberId) {
        Member member = memberRepository.findPartnerWithProfileById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_PARTNER));
        return findPartnerProfile(member);
    }

    private Partner findPartnerProfile(Member member) {
        if (member.getRole() != UserRole.PARTNER || member.getPartnerProfile() == null) {
            throw new CustomAuthException(ErrorStatus.NO_SUCH_PARTNER);
        }
        return member.getPartnerProfile();
    }

    private Admin findAdmin(Long memberId) {
        Member member = memberRepository.findAdminWithProfileById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_ADMIN));
        if (member.getAdminProfile() == null) {
            throw new CustomAuthException(ErrorStatus.NO_SUCH_ADMIN);
        }
        return member.getAdminProfile();
    }

    private void assertNotBackofficeOperator(Member member) {
        if (member.getRole() == UserRole.BACKOFFICE) {
            throw new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER);
        }
    }

    private void assertApprovalTarget(Member member) {
        if (member.getRole() != UserRole.ADMIN && member.getRole() != UserRole.PARTNER) {
            throw new CustomAuthException(ErrorStatus.MEMBER_APPROVAL_NOT_SUPPORTED);
        }
        if (member.getDeletedAt() != null) {
            throw new CustomAuthException(ErrorStatus.MEMBER_ALREADY_WITHDRAWN);
        }
    }

    private void assertPendingApproval(Member member) {
        if (member.getIsActivated() != ActivationStatus.SUSPEND) {
            throw new CustomAuthException(ErrorStatus.MEMBER_NOT_PENDING_APPROVAL);
        }
    }

    private void activatePartnerStore(Partner partner) {
        storeRepository.findByPartner(partner).ifPresent(store -> {
            store.setIsActivate(ActivationStatus.ACTIVE);
        });
    }
}
