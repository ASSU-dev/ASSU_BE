package com.assu.server.domain.common.entity.enums;

public enum Department {
    HUMANITIES("인문대학"),
    NATURAL_SCIENCE("자연과학대학"),
    LAW("법과대학"),
    SOCIAL_SCIENCE("사회과학대학"),
    ECONOMICS("경제통상대학"),
    BUSINESS("경영대학"),
    ENGINEERING("공과대학"),
    IT("IT대학"),
    LIBERAL_STUDIES("자유전공학부"),
    AI("AI대학");

    private final String displayName;

    Department(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
