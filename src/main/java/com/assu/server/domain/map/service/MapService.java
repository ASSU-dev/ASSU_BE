package com.assu.server.domain.map.service;

import com.assu.server.domain.map.dto.AdminMapResponseDTO;
import com.assu.server.domain.map.dto.MapRequestDTO;
import com.assu.server.domain.map.dto.PartnerMapResponseDTO;
import com.assu.server.domain.map.dto.StoreMapResponseDTO;
import com.assu.server.domain.map.dto.StoreMapResponseV2DTO;

import java.util.List;

public interface MapService {
    List<AdminMapResponseDTO>   getAdmins(MapRequestDTO viewport, Long memberId);
    List<PartnerMapResponseDTO> getPartners(MapRequestDTO viewport, Long memberId);
    List<StoreMapResponseDTO>   getStores(MapRequestDTO viewport, Long memberId);
    List<StoreMapResponseV2DTO> getStoresV2(MapRequestDTO viewport, Long memberId);

    List<StoreMapResponseDTO>   searchStores(String keyword);
    List<PartnerMapResponseDTO> searchPartner(String keyword, Long memberId);
    List<AdminMapResponseDTO>   searchAdmin(String keyword, Long memberId);
}
