package com.assu.server.domain.suggestion.service;

import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.suggestion.dto.BackofficeSuggestionResponseDTO;
import com.assu.server.domain.suggestion.entity.enums.SuggestionCheckFilter;

public interface BackofficeSuggestionService {
    PageResponseDTO<BackofficeSuggestionResponseDTO> getSuggestions(SuggestionCheckFilter checkFilter, String keyword, int page, int size);
    BackofficeSuggestionResponseDTO check(Long suggestionId);
}
