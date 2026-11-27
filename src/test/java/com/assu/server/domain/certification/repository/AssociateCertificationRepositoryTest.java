package com.assu.server.domain.certification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.assu.server.domain.certification.entity.AssociateCertification;
import com.assu.server.domain.certification.entity.enums.SessionStatus;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.support.CommonMockConfig;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(CommonMockConfig.class)
class AssociateCertificationRepositoryTest {

	private static final Long SESSION_ID = 9_000_001L;

	@Autowired
	private AssociateCertificationRepository associateCertificationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void findRecentByStudentIdAndStoreId_whenOldOrOtherStoreExists_thenReturnsOnlyRecentForStoreNewestFirst() {
		// given
		Student student = createStudent();
		Store store = createStore("인증 조회 가게");
		Store otherStore = createStore("인증 조회 다른 가게");
		AssociateCertification older = save(store, student, null);
		AssociateCertification newer = save(store, student, null);
		save(otherStore, student, null);
		AssociateCertification stale = save(store, student, null);
		moveCreatedAt(older, LocalDateTime.now().minusMinutes(5));
		moveCreatedAt(stale, LocalDateTime.now().minusHours(2));
		entityManager.clear();

		// when
		List<AssociateCertification> result = associateCertificationRepository.findRecentByStudentIdAndStoreId(
			student.getId(), store.getId(), LocalDateTime.now().minusMinutes(30));

		// then
		assertThat(result).extracting(AssociateCertification::getId).containsExactly(newer.getId(), older.getId());
	}

	@Test
	void findRecentBySessionIdAndStoreId_whenSummaryRowWithoutStudentExists_thenReturnsOnlyParticipantRows() {
		// given
		Student first = createStudent();
		Student second = createStudent();
		Store store = createStore("그룹 인증 가게");
		save(store, first, SESSION_ID);
		save(store, second, SESSION_ID);
		save(store, null, SESSION_ID);
		save(store, first, null);
		entityManager.clear();

		// when
		List<AssociateCertification> result = associateCertificationRepository.findRecentBySessionIdAndStoreId(
			SESSION_ID, store.getId(), LocalDateTime.now().minusMinutes(30));

		// then
		assertThat(result).extracting(certification -> certification.getStudent().getId())
			.containsExactlyInAnyOrder(first.getId(), second.getId());
	}

	@Test
	void markUsedIfUnused_whenCalledTwice_thenOnlyFirstCallClaims() {
		// given
		AssociateCertification certification = save(createStore("사용 처리 가게"), createStudent(), null);

		// when
		int first = associateCertificationRepository.markUsedIfUnused(certification.getId(), LocalDateTime.now());
		int second = associateCertificationRepository.markUsedIfUnused(certification.getId(), LocalDateTime.now());

		// then
		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
		entityManager.clear();
		assertThat(associateCertificationRepository.findById(certification.getId()).orElseThrow().getUsedAt())
			.isNotNull();
	}

	private AssociateCertification save(Store store, Student student, Long sessionId) {
		return associateCertificationRepository.saveAndFlush(AssociateCertification.builder()
			.store(store)
			.student(student)
			.sessionId(sessionId)
			.peopleNumber(sessionId == null ? 1 : 2)
			.isCertified(true)
			.status(sessionId == null ? null : SessionStatus.COMPLETED)
			.build());
	}

	private void moveCreatedAt(AssociateCertification certification, LocalDateTime createdAt) {
		entityManager.createNativeQuery("UPDATE associate_certification SET created_at = :createdAt WHERE id = :id")
			.setParameter("createdAt", createdAt)
			.setParameter("id", certification.getId())
			.executeUpdate();
	}

	private Student createStudent() {
		Member member = memberRepository.save(Member.builder()
			.role(UserRole.STUDENT)
			.isActivated(ActivationStatus.ACTIVE)
			.isLocationTermAgreed(true)
			.isMarketingTermAgreed(false)
			.build());
		Student student = studentRepository.save(Student.builder().member(member).build());
		member.setStudentProfile(student);
		memberRepository.save(member);
		return student;
	}

	private Store createStore(String name) {
		return storeRepository.save(Store.builder()
			.rate(0)
			.isActivate(ActivationStatus.ACTIVE)
			.name(name)
			.address("서울특별시 동작구 상도로 369")
			.detailAddress("1층")
			.latitude(37.0)
			.longitude(127.0)
			.build());
	}
}
