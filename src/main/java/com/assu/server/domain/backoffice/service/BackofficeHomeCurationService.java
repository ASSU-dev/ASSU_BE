package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeCurationTitleUpdateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeHomeAutoRecommendResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeHomeCurationResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeHomeCurationUpdateRequestDTO;

public interface BackofficeHomeCurationService {
    BackofficeHomeCurationResponseDTO getHomeCuration();
    BackofficeHomeCurationResponseDTO updateCurationTitle(BackofficeCurationTitleUpdateRequestDTO request);
    BackofficeHomeCurationResponseDTO updateHomeCuration(BackofficeHomeCurationUpdateRequestDTO request);
    BackofficeHomeAutoRecommendResponseDTO getAutoRecommendedCuration();
}
