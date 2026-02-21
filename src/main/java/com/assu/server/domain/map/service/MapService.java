package com.assu.server.domain.map.service;

import com.assu.server.domain.map.dto.MapRequestDTO;
import com.assu.server.domain.map.dto.MapResponseDTO;

import java.util.List;

public interface MapService {
    List<MapResponseDTO.AdminMapResponseDTO> getAdmins(MapRequestDTO.ViewOnMapDTO viewport, Long memberId);
    List<MapResponseDTO.PartnerMapResponseDTO> getPartners(MapRequestDTO.ViewOnMapDTO viewport, Long memberId);
    List<MapResponseDTO.StoreMapResponseDTO> getStores(MapRequestDTO.ViewOnMapDTO viewport, Long memberId);
    List<MapResponseDTO.StoreMapResponseV2DTO> getStoresV2(MapRequestDTO.ViewOnMapDTO viewport, Long memberId);

    List<MapResponseDTO.StoreMapResponseDTO>   searchStores(String keyword);
    List<MapResponseDTO.PartnerMapResponseDTO> searchPartner(String keyword, Long memberId);
    List<MapResponseDTO.AdminMapResponseDTO>   searchAdmin(String keyword, Long memberId);
}
