package com.assu.server.domain.map.service;

import com.assu.server.domain.map.dto.PlaceSuggestionDTO;

import java.util.List;

public interface PlaceSearchService {

    List<PlaceSuggestionDTO> unifiedSearch(String query, Integer size);
}
