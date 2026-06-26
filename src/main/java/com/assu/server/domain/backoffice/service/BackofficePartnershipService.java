package com.assu.server.domain.backoffice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;

public interface BackofficePartnershipService {
    Page<WritePartnershipResponseDTO> getPartnershipsByAdmin(Long adminId, Pageable pageable);
    Page<WritePartnershipResponseDTO> getPartnershipsByStore(Long storeId, Pageable pageable);
    Page<WritePartnershipResponseDTO> getPartnerships(Pageable pageable);
}
