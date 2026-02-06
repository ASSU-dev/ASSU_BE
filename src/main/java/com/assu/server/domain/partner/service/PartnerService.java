package com.assu.server.domain.partner.service;

import com.assu.server.domain.partner.dto.PartnerResponseDTO;

public interface PartnerService {

    PartnerResponseDTO.RandomAdminResponseDTO getRandomAdmin(Long partnerId);
}
