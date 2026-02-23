package com.assu.server.domain.partnership.service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partnership.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PartnershipService {

    WritePartnershipResponseDTO updatePartnership(WritePartnershipRequestDTO request, Long memberId);

    void recordPartnershipUsage(PartnershipFinalRequestDTO dto, Member member);

    Page<WritePartnershipResponseDTO> listPartnershipsForAdmin(Pageable pageable, Long adminId);
    Page<WritePartnershipResponseDTO> listPartnershipsForPartner(Pageable pageable, Long partnerId);

    PartnershipDetailResponseDTO getPartnership(Long partnershipId);
    List<SuspendedPaperResponseDTO> getSuspendedPapers(Long adminId);

    PartnershipStatusUpdateResponseDTO updatePartnershipStatus(Long partnershipId, PartnershipStatusUpdateRequestDTO request);

    ManualPartnershipResponseDTO createManualPartnership(ManualPartnershipRequestDTO request, Long adminId, MultipartFile contractImage);

    PartnershipDraftResponseDTO createDraftPartnership(PartnershipDraftRequestDTO request, Long adminId);

    void deletePartnership(Long paperId);

    AdminPartnershipCheckResponseDTO checkPartnershipWithPartner(Long adminId, Long partnerId);
    PartnerPartnershipCheckResponseDTO checkPartnershipWithAdmin(Long partnerId, Long adminId);
}
