package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeDocumentUrlResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeMemberDetailDTO;
import com.assu.server.domain.backoffice.dto.BackofficeMemberSummaryDTO;
import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import org.springframework.data.domain.Pageable;

public interface BackofficeMemberService {

    PageResponseDTO<BackofficeMemberSummaryDTO> listMembers(
            UserRole role,
            ActivationStatus status,
            Boolean deleted,
            Pageable pageable
    );

    PageResponseDTO<BackofficeMemberSummaryDTO> listDeletedMembers(Pageable pageable);

    BackofficeMemberDetailDTO getMemberDetail(Long memberId);

    BackofficeMemberSummaryDTO approveMember(Long memberId);

    BackofficeMemberSummaryDTO rejectMember(Long memberId);

    void forceWithdrawMember(Long memberId);

    BackofficeMemberSummaryDTO restoreMember(Long memberId);

    BackofficeDocumentUrlResponseDTO getPartnerLicenseUrl(Long memberId);

    BackofficeMemberSummaryDTO verifyPartnerLicense(Long memberId);

    BackofficeDocumentUrlResponseDTO getAdminSignImageUrl(Long memberId);
}
