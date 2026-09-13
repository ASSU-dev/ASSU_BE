package com.assu.server.domain.student.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.assu.server.domain.common.entity.enums.EnrollmentStatus;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;

class StudentTest {

	@Test
	@DisplayName("유세인트 최신 정보로 갱신하면 전공이 바뀐 경우 소속 학부도 함께 갱신된다")
	void updateStudentInfo_MajorChanged_UpdatesDepartment() {
		// given
		Major before = Major.CHRISTIAN_STUDIES;
		Student student = Student.builder()
			.name("홍길동")
			.major(before)
			.department(before.getDepartment())
			.university(University.SSU)
			.enrollmentStatus(EnrollmentStatus.ENROLLED)
			.yearSemester("1학년 1학기")
			.stamp(0)
			.build();

		Major after = Major.COMPUTER_SCIENCE;

		// when
		student.updateStudentInfo(
			"김철수", after, after.getDepartment(), EnrollmentStatus.LEAVE, "4학년 1학기");

		// then
		assertEquals(after, student.getMajor());
		assertEquals(after.getDepartment(), student.getDepartment());
		assertNotEquals(before.getDepartment(), student.getDepartment());
		assertEquals("김철수", student.getName());
		assertEquals(EnrollmentStatus.LEAVE, student.getEnrollmentStatus());
		assertEquals("4학년 1학기", student.getYearSemester());
	}
}
