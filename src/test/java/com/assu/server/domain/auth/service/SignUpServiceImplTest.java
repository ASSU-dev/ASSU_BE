package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.auth.dto.signup.AdminSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.PartnerSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.StudentTokenSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.student.StudentTokenAuthPayloadDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.SSUAuthRepository;
import com.assu.server.domain.auth.security.adapter.RealmAuthAdapter;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.domain.student.service.StudentService;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.s3.AmazonS3Manager;

@ExtendWith(MockitoExtension.class)
class SignUpServiceImplTest {

	private SignUpServiceImpl signUpService;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private StudentRepository studentRepository;

	@Mock
	private PartnerRepository partnerRepository;

	@Mock
	private AdminRepository adminRepository;

	@Mock
	private RealmAuthAdapter realmAuthAdapter;

	@Mock
	private AmazonS3Manager amazonS3Manager;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private StoreRepository storeRepository;

	@Mock
	private SSUAuthService ssuAuthService;

	@Mock
	private SSUAuthRepository ssuAuthRepository;

	@Mock
	private StudentService studentService;

	private static final String PHONE = "01012345678";

	@BeforeEach
	void setUp() {
		signUpService = new SignUpServiceImpl(
			memberRepository, studentRepository, partnerRepository, adminRepository,
			List.of(realmAuthAdapter), amazonS3Manager, jwtUtil,
			new GeometryFactory(), storeRepository, ssuAuthService, ssuAuthRepository,
			studentService);
	}

	@Test
	@DisplayName("이미 가입된 학번으로 학생 회원가입을 시도하면 EXISTED_STUDENT 예외가 발생한다")
	void signupSsuStudent_ExistingStudent_ThrowsException() {
		// 1. Given
		StudentTokenSignUpRequestDTO request = new StudentTokenSignUpRequestDTO(
			true, true, new StudentTokenAuthPayloadDTO("sToken", "20211438", University.SSU));

		USaintAuthResponseDTO authResponse =
			USaintAuthResponseDTO.of("20211438", "홍길동", "재학", "4학년 1학기", "컴퓨터학부");
		when(ssuAuthService.uSaintAuth(any(USaintAuthRequestDTO.class))).thenReturn(authResponse);
		when(ssuAuthRepository.existsByStudentNumber("20211438")).thenReturn(true);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> signUpService.signupSsuStudent(request));

		// 3. Then
		assertEquals(ErrorStatus.EXISTED_STUDENT, exception.getCode());
		verify(memberRepository, never()).save(any());
		verify(studentRepository, never()).save(any());
	}

	@Test
	@DisplayName("이미 가입된 전화번호로 파트너 회원가입을 시도하면 EXISTED_PHONE 예외가 발생한다")
	void signupPartner_ExistingPhone_ThrowsException() {
		// 1. Given (전화번호 중복 검사에서 걸리므로 나머지 필드는 사용되지 않음)
		PartnerSignUpRequestDTO request = new PartnerSignUpRequestDTO(PHONE, true, true, null, null);
		when(partnerRepository.existsByPhoneNum(PHONE)).thenReturn(true);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> signUpService.signupPartner(request, null));

		// 3. Then
		assertEquals(ErrorStatus.EXISTED_PHONE, exception.getCode());
		verify(memberRepository, never()).save(any());
	}

	@Test
	@DisplayName("관리자로 이미 등록된 전화번호로 관리자 회원가입을 시도하면 EXISTED_PHONE 예외가 발생한다")
	void signupAdmin_ExistingPhone_ThrowsException() {
		// 1. Given (파트너에는 없지만 관리자에 이미 존재하는 번호)
		AdminSignUpRequestDTO request = new AdminSignUpRequestDTO(PHONE, true, true, null, null);
		when(partnerRepository.existsByPhoneNum(PHONE)).thenReturn(false);
		when(adminRepository.existsByPhoneNum(PHONE)).thenReturn(true);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> signUpService.signupAdmin(request, null));

		// 3. Then
		assertEquals(ErrorStatus.EXISTED_PHONE, exception.getCode());
		verify(memberRepository, never()).save(any());
	}
}
