package com.assu.server.domain.partnership.service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partnership.dto.PartnershipFinalRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PartnershipService {

    PartnershipResponseDTO.WritePartnershipResponseDTO updatePartnership(PartnershipRequestDTO.WritePartnershipRequestDTO request, Long memberId);
    
    void recordPartnershipUsage(PartnershipFinalRequestDTO dto, Member member);

    List<PartnershipResponseDTO.WritePartnershipResponseDTO> listPartnershipsForAdmin(boolean all, Long partnerId);
    List<PartnershipResponseDTO.WritePartnershipResponseDTO> listPartnershipsForPartner(boolean all, Long adminId);

    PartnershipResponseDTO.GetPartnershipDetailResponseDTO getPartnership(Long partnershipId);
    List<PartnershipResponseDTO.SuspendedPaperDTO> getSuspendedPapers(Long adminId);

    PartnershipResponseDTO.UpdateResponseDTO updatePartnershipStatus(Long partnershipId, PartnershipRequestDTO.UpdateRequestDTO request);

    PartnershipResponseDTO.ManualPartnershipResponseDTO createManualPartnership(PartnershipRequestDTO.ManualPartnershipRequestDTO request, Long adminId, MultipartFile contractImage);

    PartnershipResponseDTO.CreateDraftResponseDTO createDraftPartnership(PartnershipRequestDTO.CreateDraftRequestDTO request, Long adminId);

    void deletePartnership(Long paperId);

    PartnershipResponseDTO.AdminPartnershipWithPartnerResponseDTO checkPartnershipWithPartner(Long adminId, Long partnerId);
    PartnershipResponseDTO.PartnerPartnershipWithAdminResponseDTO checkPartnershipWithAdmin(Long partnerId, Long adminId);
}
