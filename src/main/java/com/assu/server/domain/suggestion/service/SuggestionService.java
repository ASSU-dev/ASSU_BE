package com.assu.server.domain.suggestion.service;

import com.assu.server.domain.suggestion.dto.GetSuggestionAdminsDTO;
import com.assu.server.domain.suggestion.dto.GetSuggestionResponseDTO;
import com.assu.server.domain.suggestion.dto.WriteSuggestionRequestDTO;
import com.assu.server.domain.suggestion.dto.WriteSuggestionResponseDTO;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface SuggestionService {

    WriteSuggestionResponseDTO writeSuggestion(
            @RequestBody WriteSuggestionRequestDTO request,
            Long userId
    );

    List<GetSuggestionResponseDTO> getSuggestions(Long adminId);

    GetSuggestionAdminsDTO getSuggestionAdmins(Long userId);
}
