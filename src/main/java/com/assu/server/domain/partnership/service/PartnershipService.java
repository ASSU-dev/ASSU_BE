package com.assu.server.domain.partnership.service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partnership.dto.AdminPartnershipCheckResponseDTO;
import com.assu.server.domain.partnership.dto.ManualPartnershipRequestDTO;
import com.assu.server.domain.partnership.dto.ManualPartnershipResponseDTO;
import com.assu.server.domain.partnership.dto.PartnerPartnershipCheckResponseDTO;
import com.assu.server.domain.partnership.dto.PartnershipDetailResponseDTO;
import com.assu.server.domain.partnership.dto.PartnershipDraftRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipDraftResponseDTO;
import com.assu.server.domain.partnership.dto.PartnershipFinalRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipStatusUpdateRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipStatusUpdateResponseDTO;
import com.assu.server.domain.partnership.dto.SuspendedPaperResponseDTO;
import com.assu.server.domain.partnership.dto.WritePartnershipRequestDTO;
import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PartnershipService {

    WritePartnershipResponseDTO updatePartnership(WritePartnershipRequestDTO request, Long memberId);

    void recordPartnershipUsage(PartnershipFinalRequestDTO dto, Member member);

    List<WritePartnershipResponseDTO> listPartnershipsForAdmin(boolean all, Long partnerId);
    List<WritePartnershipResponseDTO> listPartnershipsForPartner(boolean all, Long adminId);

    PartnershipDetailResponseDTO getPartnership(Long partnershipId);
    List<SuspendedPaperResponseDTO> getSuspendedPapers(Long adminId);

    PartnershipStatusUpdateResponseDTO updatePartnershipStatus(Long partnershipId, PartnershipStatusUpdateRequestDTO request);

    ManualPartnershipResponseDTO createManualPartnership(ManualPartnershipRequestDTO request, Long adminId, MultipartFile contractImage);

    PartnershipDraftResponseDTO createDraftPartnership(PartnershipDraftRequestDTO request, Long adminId);

    void deletePartnership(Long paperId);

    AdminPartnershipCheckResponseDTO checkPartnershipWithPartner(Long adminId, Long partnerId);
    PartnerPartnershipCheckResponseDTO checkPartnershipWithAdmin(Long partnerId, Long adminId);
}
