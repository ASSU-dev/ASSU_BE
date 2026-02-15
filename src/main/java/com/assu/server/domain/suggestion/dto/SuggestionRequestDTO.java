package com.assu.server.domain.suggestion.dto;

import lombok.Getter;

public class SuggestionRequestDTO {

    @Getter
    public static class WriteSuggestionRequestDTO{
        private Long adminId;
        private String storeName;
        private String benefit;
    }
}
