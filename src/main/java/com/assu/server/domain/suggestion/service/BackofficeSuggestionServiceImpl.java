package com.assu.server.domain.suggestion.service;

import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.suggestion.dto.BackofficeSuggestionResponseDTO;
import com.assu.server.domain.suggestion.entity.Suggestion;
import com.assu.server.domain.suggestion.entity.enums.SuggestionCheckFilter;
import com.assu.server.domain.suggestion.repository.SuggestionRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BackofficeSuggestionServiceImpl implements BackofficeSuggestionService {

    private final SuggestionRepository suggestionRepository;

    @Override
    public PageResponseDTO<BackofficeSuggestionResponseDTO> getSuggestions(SuggestionCheckFilter checkFilter, String keyword, int page, int size) {
        if (page < 1) throw new GeneralException(ErrorStatus.PAGE_UNDER_ONE);
        if (size < 1 || size > 200) throw new GeneralException(ErrorStatus.PAGE_SIZE_INVALID);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        boolean includeChecked = checkFilter != SuggestionCheckFilter.UNCHECKED;
        boolean includeUnchecked = checkFilter != SuggestionCheckFilter.CHECKED;
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.strip();

        return PageResponseDTO.of(
                suggestionRepository.findForBackoffice(includeChecked, includeUnchecked, normalizedKeyword, pageable)
                        .map(BackofficeSuggestionResponseDTO::from)
        );
    }

    @Override
    @Transactional
    public BackofficeSuggestionResponseDTO check(Long suggestionId) {
        Suggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_SUGGESTION));
        suggestion.check();
        return BackofficeSuggestionResponseDTO.from(suggestion);
    }
}
