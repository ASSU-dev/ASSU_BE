package com.assu.server.domain.suggestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.suggestion.dto.BackofficeSuggestionResponseDTO;
import com.assu.server.domain.suggestion.entity.Suggestion;
import com.assu.server.domain.suggestion.entity.enums.SuggestionCheckFilter;
import com.assu.server.domain.suggestion.repository.SuggestionRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class BackofficeSuggestionServiceImplTest {

	@InjectMocks
	private BackofficeSuggestionServiceImpl backofficeSuggestionService;

	@Mock
	private SuggestionRepository suggestionRepository;

	@Test
	@DisplayName("페이지 번호가 1 미만이면 PAGE_UNDER_ONE 예외가 발생한다")
	void getSuggestions_whenPageUnderOne_thenThrowsException() {
		// when
		GeneralException exception = assertThrows(GeneralException.class,
			() -> backofficeSuggestionService.getSuggestions(SuggestionCheckFilter.ALL, null, 0, 20));

		// then
		assertEquals(ErrorStatus.PAGE_UNDER_ONE, exception.getCode());
		verify(suggestionRepository, never()).findForBackoffice(anyBoolean(), anyBoolean(), any(), any());
	}

	@Test
	@DisplayName("페이지 크기가 200을 초과하면 PAGE_SIZE_INVALID 예외가 발생한다")
	void getSuggestions_whenPageSizeTooLarge_thenThrowsException() {
		// when
		GeneralException exception = assertThrows(GeneralException.class,
			() -> backofficeSuggestionService.getSuggestions(SuggestionCheckFilter.ALL, null, 1, 201));

		// then
		assertEquals(ErrorStatus.PAGE_SIZE_INVALID, exception.getCode());
	}

	@Test
	@DisplayName("UNCHECKED 필터는 미확인 건의만 조회하고 공백 키워드는 null로 전달한다")
	void getSuggestions_whenUncheckedFilterAndBlankKeyword_thenQueriesUncheckedOnly() {
		// given
		when(suggestionRepository.findForBackoffice(eq(false), eq(true), isNull(), any(Pageable.class)))
			.thenReturn(Page.empty());

		// when
		backofficeSuggestionService.getSuggestions(SuggestionCheckFilter.UNCHECKED, "  ", 1, 20);

		// then
		verify(suggestionRepository).findForBackoffice(eq(false), eq(true), isNull(), any(Pageable.class));
	}

	@Test
	@DisplayName("CHECKED 필터는 확인된 건의만 조회하고 키워드 앞뒤 공백을 제거한다")
	void getSuggestions_whenCheckedFilterAndKeyword_thenQueriesCheckedOnly() {
		// given
		when(suggestionRepository.findForBackoffice(eq(true), eq(false), eq("스타벅스"), any(Pageable.class)))
			.thenReturn(Page.empty());

		// when
		backofficeSuggestionService.getSuggestions(SuggestionCheckFilter.CHECKED, " 스타벅스 ", 1, 20);

		// then
		verify(suggestionRepository).findForBackoffice(eq(true), eq(false), eq("스타벅스"), any(Pageable.class));
	}

	@Test
	@DisplayName("ALL 필터 조회 결과를 작성자·학생회 정보가 포함된 DTO 페이지로 변환한다")
	void getSuggestions_whenAllFilter_thenReturnsMappedPage() {
		// given
		Suggestion suggestion = createSuggestion();
		Pageable pageable = PageRequest.of(0, 20);
		when(suggestionRepository.findForBackoffice(eq(true), eq(true), isNull(), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(suggestion), pageable, 1));

		// when
		PageResponseDTO<BackofficeSuggestionResponseDTO> response =
			backofficeSuggestionService.getSuggestions(SuggestionCheckFilter.ALL, null, 1, 20);

		// then
		assertEquals(1, response.totalElements());
		BackofficeSuggestionResponseDTO item = response.items().get(0);
		assertEquals(5L, item.suggestionId());
		assertEquals("홍길동", item.studentName());
		assertEquals("숭실대학교 총학생회", item.adminName());
		assertEquals("스타벅스 숭실대점", item.storeName());
		assertEquals("NORMAL", item.reportedStatus());
		assertFalse(item.isChecked());
		assertNull(item.checkedAt());
	}

	@Test
	@DisplayName("존재하지 않는 건의를 확인 처리하면 NO_SUCH_SUGGESTION 예외가 발생한다")
	void check_whenSuggestionNotFound_thenThrowsException() {
		// given
		when(suggestionRepository.findById(5L)).thenReturn(Optional.empty());

		// when
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> backofficeSuggestionService.check(5L));

		// then
		assertEquals(ErrorStatus.NO_SUCH_SUGGESTION, exception.getCode());
	}

	@Test
	@DisplayName("미확인 건의를 확인 처리하면 확인 일시가 기록된다")
	void check_whenUnchecked_thenRecordsCheckedAt() {
		// given
		Suggestion suggestion = createSuggestion();
		when(suggestionRepository.findById(5L)).thenReturn(Optional.of(suggestion));

		// when
		BackofficeSuggestionResponseDTO response = backofficeSuggestionService.check(5L);

		// then
		assertTrue(response.isChecked());
		assertNotNull(response.checkedAt());
		assertEquals(suggestion.getCheckedAt(), response.checkedAt());
	}

	@Test
	@DisplayName("이미 확인된 건의를 다시 확인 처리해도 최초 확인 일시를 유지한다")
	void check_whenAlreadyChecked_thenKeepsFirstCheckedAt() {
		// given
		LocalDateTime firstCheckedAt = LocalDateTime.of(2026, 11, 20, 10, 0);
		Suggestion suggestion = Suggestion.builder()
			.id(5L).storeName("스타벅스 숭실대점").content("할인 부탁드립니다").checkedAt(firstCheckedAt).build();
		when(suggestionRepository.findById(5L)).thenReturn(Optional.of(suggestion));

		// when
		BackofficeSuggestionResponseDTO response = backofficeSuggestionService.check(5L);

		// then
		assertTrue(response.isChecked());
		assertEquals(firstCheckedAt, response.checkedAt());
	}

	private Suggestion createSuggestion() {
		Admin admin = Admin.builder().id(10L).name("숭실대학교 총학생회").isPhoneVerified(false).build();
		Student student = Student.builder().id(3L).name("홍길동").build();
		return Suggestion.builder()
			.id(5L).admin(admin).student(student)
			.storeName("스타벅스 숭실대점").content("아메리카노 10% 할인 부탁드립니다").build();
	}
}
