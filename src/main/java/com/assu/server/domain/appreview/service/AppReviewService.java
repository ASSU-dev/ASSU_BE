package com.assu.server.domain.appreview.service;

import com.assu.server.domain.appreview.dto.AppReviewRequestDTO;

public interface AppReviewService {
    void create(AppReviewRequestDTO request, Long memberId);
}