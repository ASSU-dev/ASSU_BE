package com.assu.server.domain.partnership.service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partnership.dto.PartnershipFinalRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PartnershipService {

    // 제휴 제안서 수정
    PartnershipResponseDTO.WritePartnershipResponseDTO updatePartnership(
            PartnershipRequestDTO.WritePartnershipRequestDTO request,
            Long memberId
    );
    
    void recordPartnershipUsage(PartnershipFinalRequestDTO dto, Member member);

    // 제휴업체/관리자 맺은 제휴 리스트
    List<PartnershipResponseDTO.WritePartnershipResponseDTO> listPartnershipsForAdmin(boolean all, Long partnerId);
    List<PartnershipResponseDTO.WritePartnershipResponseDTO> listPartnershipsForPartner(boolean all, Long adminId);

    // 제휴 제안서 조회
    PartnershipResponseDTO.GetPartnershipDetailResponseDTO getPartnership(Long partnershipId);
    List<PartnershipResponseDTO.SuspendedPaperDTO> getSuspendedPapers(Long adminId);

    // 제휴 상태 업데이트
    PartnershipResponseDTO.UpdateResponseDTO updatePartnershipStatus(Long partnershipId, PartnershipRequestDTO.UpdateRequestDTO request);

    // 제휴 수동 등록
    PartnershipResponseDTO.ManualPartnershipResponseDTO createManualPartnership(
            PartnershipRequestDTO.ManualPartnershipRequestDTO request,
            Long adminId,
            MultipartFile contractImage
    );

    // 빈 제휴제안서 만들기
    PartnershipResponseDTO.CreateDraftResponseDTO createDraftPartnership(PartnershipRequestDTO.CreateDraftRequestDTO request, Long adminId);

    // 제휴 계약서 삭제
    void deletePartnership(Long paperId);

    // 채팅방 내 제휴 계약서 상태 확인
    PartnershipResponseDTO.AdminPartnershipWithPartnerResponseDTO checkPartnershipWithPartner(Long adminId, Long partnerId); // 관리자가 조회
    PartnershipResponseDTO.PartnerPartnershipWithAdminResponseDTO checkPartnershipWithAdmin(Long partnerId, Long adminId); // 제휴업체가 조회
}
