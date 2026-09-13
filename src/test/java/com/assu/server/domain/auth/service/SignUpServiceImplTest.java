package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.auth.dto.signup.AdminSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.PartnerSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.SignUpResponseDTO;
import com.assu.server.domain.auth.dto.signup.StudentTokenSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.student.StudentTokenAuthPayloadDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;
import com.assu.server.domain.auth.entity.SSUAuth;
import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.SSUAuthRepository;
import com.assu.server.domain.auth.security.adapter.RealmAuthAdapter;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.common.entity.enums.EnrollmentStatus;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.entity.Student;
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

		Member member = mock(Member.class);
		when(member.isWithdrawn()).thenReturn(false);

		SSUAuth ssuAuth = mock(SSUAuth.class);
		when(ssuAuth.getMember()).thenReturn(member);
		when(ssuAuthRepository.findByStudentNumber("20211438")).thenReturn(Optional.of(ssuAuth));

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> signUpService.signupSsuStudent(request));

		// 3. Then
		assertEquals(ErrorStatus.EXISTED_STUDENT, exception.getCode());
		verify(member, never()).restore();
		verify(memberRepository, never()).save(any());
		verify(studentRepository, never()).save(any());
	}

	@Test
	@DisplayName("정상적인 학생 회원가입 시 저장된 학생 ID로 제휴 동기화를 호출한 뒤 JWT를 발급한다")
	void signupSsuStudent_Success_SyncsUserPapersBeforeIssuingTokens() {
		// 1. Given
		StudentTokenSignUpRequestDTO request = new StudentTokenSignUpRequestDTO(
			true, true, new StudentTokenAuthPayloadDTO("sToken", "20211438", University.SSU));

		USaintAuthResponseDTO authResponse =
			USaintAuthResponseDTO.of("20211438", "홍길동", "재학", "4학년 1학기", "컴퓨터학부");
		when(ssuAuthService.uSaintAuth(any(USaintAuthRequestDTO.class))).thenReturn(authResponse);
		when(ssuAuthRepository.findByStudentNumber("20211438")).thenReturn(Optional.empty());
		when(realmAuthAdapter.supports(AuthRealm.SSU)).thenReturn(true);

		Member member = mock(Member.class);
		when(member.getId()).thenReturn(1L);
		when(member.getRole()).thenReturn(UserRole.STUDENT);
		when(memberRepository.save(any())).thenReturn(member);

		Student student = mock(Student.class);
		when(student.getId()).thenReturn(99L);
		when(studentRepository.save(any())).thenReturn(student);

		when(jwtUtil.issueTokens(1L, "20211438", UserRole.STUDENT, "SSU"))
			.thenReturn(TokensDTO.of("access-token", "refresh-token"));

		// 2. When
		signUpService.signupSsuStudent(request);

		// 3. Then
		verify(studentService).syncUserPapersForStudent(99L);

		InOrder inOrder = inOrder(studentService, jwtUtil);
		inOrder.verify(studentService).syncUserPapersForStudent(99L);
		inOrder.verify(jwtUtil).issueTokens(1L, "20211438", UserRole.STUDENT, "SSU");
	}

	@Test
	@DisplayName("탈퇴한 학생이 동일 학번으로 재가입하면 기존 계정을 복구하고 JWT를 발급한다")
	void signupSsuStudent_WithdrawnStudent_RestoresAccountAndIssuesTokens() {
		// 1. Given
		StudentTokenSignUpRequestDTO request = new StudentTokenSignUpRequestDTO(
			true, true, new StudentTokenAuthPayloadDTO("sToken", "20211438", University.SSU));

		Member member = stubWithdrawnStudentAccount();

		// 2. When
		SignUpResponseDTO response = signUpService.signupSsuStudent(request);

		// 3. Then
		Major major = Major.fromDisplayName("컴퓨터학부");
		verify(member, times(1)).restore();
		verify(memberRepository, times(1)).save(member);
		verify(studentRepository, times(1)).save(any(Student.class));
		verify(studentService, times(1)).syncUserPapersForStudent(99L);
		verify(realmAuthAdapter, never()).registerCredentials(any(), anyString(), anyString());

		assertEquals(1L, response.memberId());
		assertEquals("access-token", response.tokens().accessToken());
		assertEquals(major.getDisplayName(), response.basicInfo().major());
	}

	@Test
	@DisplayName("탈퇴한 학생이 재가입하면 재가입 요청의 약관 동의값으로 갱신한다")
	void signupSsuStudent_WithdrawnStudent_UpdatesTermAgreements() {
		// 1. Given (마케팅 미동의, 위치 동의)
		StudentTokenSignUpRequestDTO request = new StudentTokenSignUpRequestDTO(
			false, true, new StudentTokenAuthPayloadDTO("sToken", "20211438", University.SSU));

		Member member = stubWithdrawnStudentAccount();

		// 2. When
		signUpService.signupSsuStudent(request);

		// 3. Then
		verify(member, times(1)).updateTermAgreements(true, false);
	}

	@Test
	@DisplayName("탈퇴한 학생이 재가입하면 유세인트 최신 학적 정보로 프로필을 갱신한다")
	void signupSsuStudent_WithdrawnStudent_UpdatesStudentInfoFromUSaint() {
		// 1. Given
		StudentTokenSignUpRequestDTO request = new StudentTokenSignUpRequestDTO(
			true, true, new StudentTokenAuthPayloadDTO("sToken", "20211438", University.SSU));

		Member member = stubWithdrawnStudentAccount();
		Student student = member.getStudentProfile();

		// 2. When
		signUpService.signupSsuStudent(request);

		// 3. Then
		Major major = Major.fromDisplayName("컴퓨터학부");
		verify(student, times(1)).updateStudentInfo(
			"홍길동", major, major.getDepartment(), EnrollmentStatus.ENROLLED, "4학년 1학기");
	}

	/**
	 * 탈퇴한 학생 계정(학번 20211438)이 존재하는 상황을 구성한다.
	 */
	private Member stubWithdrawnStudentAccount() {
		USaintAuthResponseDTO authResponse =
			USaintAuthResponseDTO.of("20211438", "홍길동", "재학", "4학년 1학기", "컴퓨터학부");
		when(ssuAuthService.uSaintAuth(any(USaintAuthRequestDTO.class))).thenReturn(authResponse);

		Major major = Major.fromDisplayName("컴퓨터학부");

		Student student = mock(Student.class);
		when(student.getId()).thenReturn(99L);
		when(student.getName()).thenReturn("홍길동");
		when(student.getUniversity()).thenReturn(University.SSU);
		when(student.getDepartment()).thenReturn(major.getDepartment());
		when(student.getMajor()).thenReturn(major);

		Member member = mock(Member.class);
		when(member.getId()).thenReturn(1L);
		when(member.getRole()).thenReturn(UserRole.STUDENT);
		when(member.getIsActivated()).thenReturn(ActivationStatus.ACTIVE);
		when(member.isWithdrawn()).thenReturn(true);
		when(member.getStudentProfile()).thenReturn(student);

		SSUAuth ssuAuth = mock(SSUAuth.class);
		when(ssuAuth.getMember()).thenReturn(member);
		when(ssuAuthRepository.findByStudentNumber("20211438")).thenReturn(Optional.of(ssuAuth));

		when(jwtUtil.issueTokens(1L, "20211438", UserRole.STUDENT, "SSU"))
			.thenReturn(TokensDTO.of("access-token", "refresh-token"));

		return member;
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
