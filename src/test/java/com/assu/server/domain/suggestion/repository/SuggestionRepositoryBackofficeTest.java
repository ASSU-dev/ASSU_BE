package com.assu.server.domain.suggestion.repository;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.domain.suggestion.entity.Suggestion;
import com.assu.server.support.CommonMockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class SuggestionRepositoryBackofficeTest {

    @Autowired
    private SuggestionRepository suggestionRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private MemberRepository memberRepository;

    private final Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));

    private Suggestion checked;
    private Suggestion unchecked;

    @BeforeEach
    void setUp() {
        Admin admin = createAdmin();
        Student student = createStudent();
        checked = createSuggestion(admin, student, "스타벅스 숭실대점", "아메리카노 할인");
        checked.check();
        unchecked = createSuggestion(admin, student, "역전할머니 맥주", "스타벅스 말고 술집도 제휴해주세요");
    }

    @Test
    void findForBackoffice_whenAllIncluded_thenReturnsLatestFirst() {
        // when
        Page<Suggestion> result = suggestionRepository.findForBackoffice(true, true, null, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Suggestion::getId)
                .containsExactly(unchecked.getId(), checked.getId());
    }

    @Test
    void findForBackoffice_whenUncheckedOnly_thenExcludesCheckedSuggestion() {
        // when
        Page<Suggestion> result = suggestionRepository.findForBackoffice(false, true, null, pageable);

        // then
        assertThat(result.getContent()).extracting(Suggestion::getId).containsExactly(unchecked.getId());
    }

    @Test
    void findForBackoffice_whenCheckedOnly_thenExcludesUncheckedSuggestion() {
        // when
        Page<Suggestion> result = suggestionRepository.findForBackoffice(true, false, null, pageable);

        // then
        assertThat(result.getContent()).extracting(Suggestion::getId).containsExactly(checked.getId());
    }

    @Test
    void findForBackoffice_whenKeyword_thenMatchesStoreNameOrContent() {
        // when
        Page<Suggestion> byBoth = suggestionRepository.findForBackoffice(true, true, "스타벅스", pageable);
        Page<Suggestion> byStoreName = suggestionRepository.findForBackoffice(true, true, "맥주", pageable);

        // then
        assertThat(byBoth.getTotalElements()).isEqualTo(2);
        assertThat(byStoreName.getContent()).extracting(Suggestion::getId).containsExactly(unchecked.getId());
    }

    private Member createMember(UserRole role) {
        return memberRepository.save(Member.builder()
                .role(role)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());
    }

    private Admin createAdmin() {
        return adminRepository.save(Admin.builder()
                .member(createMember(UserRole.ADMIN))
                .name("숭실 총학생회")
                .university(University.SSU)
                .isPhoneVerified(true)
                .officeAddress("서울특별시 동작구 상도로 369")
                .build());
    }

    private Student createStudent() {
        return studentRepository.save(Student.builder()
                .member(createMember(UserRole.STUDENT))
                .name("홍길동")
                .university(University.SSU)
                .build());
    }

    private Suggestion createSuggestion(Admin admin, Student student, String storeName, String content) {
        return suggestionRepository.save(Suggestion.builder()
                .admin(admin)
                .student(student)
                .storeName(storeName)
                .content(content)
                .build());
    }
}
