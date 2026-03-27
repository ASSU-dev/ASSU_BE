package com.assu.server.domain.common.entity.enums;

public enum Major {
    // 인문대학
    CHRISTIAN_STUDIES(Department.HUMANITIES, "기독교학과"),
    KOREAN_LITERATURE(Department.HUMANITIES, "국어국문학과"),
    ENGLISH_LITERATURE(Department.HUMANITIES, "영어영문학과"),
    GERMAN_LITERATURE(Department.HUMANITIES, "독어독문학과"),
    FRENCH_LITERATURE(Department.HUMANITIES, "불어불문학과"),
    CHINESE_LITERATURE(Department.HUMANITIES, "중어중문학과"),
    JAPANESE_LITERATURE(Department.HUMANITIES, "일어일문학과"),
    PHILOSOPHY(Department.HUMANITIES, "철학과"),
    HISTORY(Department.HUMANITIES, "사학과"),
    CREATIVE_ARTS(Department.HUMANITIES, "예술창작학부"),
    SPORTS(Department.HUMANITIES, "스포츠학부"),

    // 자연과학대학
    MATHEMATICS(Department.NATURAL_SCIENCE, "수학과"),
    CHEMISTRY(Department.NATURAL_SCIENCE, "화학과"),
    BIOMEDICAL_SYSTEMS(Department.NATURAL_SCIENCE, "의생명시스템학부"),
    PHYSICS(Department.NATURAL_SCIENCE, "물리학과"),
    STATISTICS_ACTUARIAL(Department.NATURAL_SCIENCE, "정보통계ㆍ보험수리학과"),

    // 법과대학
    LAW(Department.LAW, "법학과"),
    INTERNATIONAL_LAW(Department.LAW, "국제법무학과"),

    // 사회과학대학
    SOCIAL_WELFARE(Department.SOCIAL_SCIENCE, "사회복지학부"),
    POLITICAL_SCIENCE(Department.SOCIAL_SCIENCE, "정치외교학과"),
    MEDIA_COMMUNICATION(Department.SOCIAL_SCIENCE, "언론홍보학과"),
    PUBLIC_ADMINISTRATION(Department.SOCIAL_SCIENCE, "행정학부"),
    INFORMATION_SOCIETY(Department.SOCIAL_SCIENCE, "정보사회학과"),
    LIFELONG_EDUCATION(Department.SOCIAL_SCIENCE, "평생교육학과"),

    // 경제통상대학
    ECONOMICS(Department.ECONOMICS, "경제학과"),
    FINANCIAL_ECONOMICS(Department.ECONOMICS, "금융경제학과"),
    GLOBAL_TRADE(Department.ECONOMICS, "글로벌통상학과"),
    INTERNATIONAL_TRADE(Department.ECONOMICS, "국제무역학과"),

    // 경영대학
    BUSINESS_ADMINISTRATION(Department.BUSINESS, "경영학부"),
    ACCOUNTING(Department.BUSINESS, "회계학과"),
    VENTURE_MANAGEMENT(Department.BUSINESS, "벤처경영학과"),
    WELFARE_MANAGEMENT(Department.BUSINESS, "복지경영학과"),
    VENTURE_SME(Department.BUSINESS, "벤처중소기업학과"),
    FINANCE(Department.BUSINESS, "금융학부"),
    INNOVATION_MANAGEMENT(Department.BUSINESS, "혁신경영학과"),
    ACCOUNTING_TAX(Department.BUSINESS, "회계세무학과"),

    // 공과대학
    CHEMICAL_ENGINEERING(Department.ENGINEERING, "화학공학과"),
    ELECTRICAL_ENGINEERING(Department.ENGINEERING, "전기공학부"),
    ARCHITECTURE(Department.ENGINEERING, "건축학부"),
    INDUSTRIAL_INFO_SYSTEMS(Department.ENGINEERING, "산업ㆍ정보시스템공학과"),
    MECHANICAL_ENGINEERING(Department.ENGINEERING, "기계공학부"),
    MATERIALS_SCIENCE(Department.ENGINEERING, "신소재공학과"),

    // IT대학
    SW(Department.IT, "소프트웨어학부"),
    GM(Department.IT, "글로벌미디어학부"),
    COM(Department.IT, "컴퓨터학부"),
    EE(Department.IT, "전자정보공학부"),
    IP(Department.IT, "정보보호학과"),
    AI(Department.IT, "AI융합학부"),
    MB(Department.IT, "미디어경영학과"),

    // 자유전공학부
    LIBERAL_ARTS(Department.LIBERAL_ARTS, "자유전공학부");

    private final Department department;
    private final String displayName;

    Major(Department department, String displayName) {
        this.department = department;
        this.displayName = displayName;
    }

    public Department getDepartment() {
        return department;
    }

    public String getDisplayName() {
        return displayName;
    }
}
