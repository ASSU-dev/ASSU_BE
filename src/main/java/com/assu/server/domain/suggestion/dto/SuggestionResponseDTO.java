    package com.assu.server.domain.suggestion.dto;

    import com.assu.server.domain.user.entity.enums.EnrollmentStatus;
    import com.assu.server.domain.user.entity.enums.Major;
    import lombok.AllArgsConstructor;
    import lombok.Builder;
    import lombok.Getter;
    import lombok.NoArgsConstructor;

    import java.time.LocalDateTime;

    public class SuggestionResponseDTO {

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class WriteSuggestionResponseDTO {
            private Long suggestionId;
            private Long userId;
            private Long adminId;
            private String storeName;
            private String suggestionBenefit;
        }

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class GetSuggestionResponseDTO {
            private Long suggestionId;
            private LocalDateTime createdAt;
            private String storeName;
            private String content;
            private Major studentMajor;
            private EnrollmentStatus enrollmentStatus;
        }

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class GetSuggestionAdminsDTO {
            private Long adminId;
            private String adminName;
            private Long departId;
            private String departName;
            private Long majorId;
            private String majorName;
        }
    }
