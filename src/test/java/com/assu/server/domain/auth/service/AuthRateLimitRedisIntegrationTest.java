package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.config.IpRateLimitInterceptor;
import com.assu.server.infra.aligo.client.AligoSmsClient;
import com.assu.server.infra.aligo.dto.AligoSendResponse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

// CI(GitHub Actions가 CI 환경 변수를 설정)에서는 Docker가 없으면 건너뛰지 않고 실패시켜, 테스트가 조용히 빠지지 않게 한다
@Testcontainers
@EnabledIf("dockerAvailableOrCi")
class AuthRateLimitRedisIntegrationTest {

	static boolean dockerAvailableOrCi() {
		return System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable();
	}

	@Container
	private static final GenericContainer<?> REDIS =
		new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

	private static final String PHONE = "01012345678";
	private static final String CODE_KEY = PHONE;
	private static final String FAIL_KEY = "phone-verify-fail:" + PHONE;
	private static final String VERIFIED_KEY = "phone-verified:" + PHONE;
	private static final String COOLDOWN_KEY = "phone-send-cooldown:" + PHONE;
	private static final String COUNT_KEY = "phone-send-count:" + PHONE;
	private static final String WINDOW_KEY = "phone-send-window:" + PHONE;
	private static final String EMAIL = "user@assu.com";
	private static final String LOGIN_KEY = "login-fail:" + EMAIL;

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;

	private CommonAuthRepository commonAuthRepository;
	private AligoSmsClient aligoSmsClient;
	private LoginAttemptLimiter loginAttemptLimiter;
	private PhoneAuthServiceImpl phoneAuthService;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
	}

	@AfterAll
	static void disconnect() {
		connectionFactory.destroy();
	}

	@BeforeEach
	void setUp() {
		redisTemplate.execute(connection -> {
			connection.serverCommands().flushAll();
			return null;
		}, true);
		commonAuthRepository = mock(CommonAuthRepository.class);
		aligoSmsClient = mock(AligoSmsClient.class);
		loginAttemptLimiter = new LoginAttemptLimiter(redisTemplate, new SimpleMeterRegistry(), commonAuthRepository);
		phoneAuthService = new PhoneAuthServiceImpl(redisTemplate, aligoSmsClient,
			mock(PartnerRepository.class), mock(AdminRepository.class), new SimpleMeterRegistry());
	}

	private static AligoSendResponse aligoResponse(String resultCode) {
		AligoSendResponse response = new AligoSendResponse();
		response.setResult_code(resultCode);
		return response;
	}

	private long ttlSeconds(String key) {
		Long ttl = redisTemplate.getExpire(key);
		return ttl == null ? -2 : ttl;
	}

	private ErrorStatus verifyResult(String authNumber) {
		try {
			phoneAuthService.verifyAuthNumber(PHONE, authNumber);
			return null;
		} catch (CustomAuthException exception) {
			return (ErrorStatus) exception.getCode();
		}
	}

	// ===== 로그인 시도 제한 =====

	@Test
	@DisplayName("일반 계정은 5번까지 통과하고 6번째부터 차단되며 키에 15분 차단 TTL이 걸린다")
	void login_whenCommonAccountFailsFiveTimes_thenBlocksWithFifteenMinuteTtl() {
		// given
		when(commonAuthRepository.findRoleByEmail(EMAIL)).thenReturn(Optional.of(UserRole.PARTNER));

		// when
		for (int i = 0; i < 5; i++) {
			assertDoesNotThrow(() -> loginAttemptLimiter.checkAttempt(EMAIL));
		}
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> loginAttemptLimiter.checkAttempt(EMAIL));

		// then
		assertEquals(ErrorStatus.LOGIN_ATTEMPTS_EXCEEDED, exception.getCode());
		long ttl = ttlSeconds(LOGIN_KEY);
		assertTrue(ttl > 890 && ttl <= 900, "ttl=" + ttl);
	}

	@Test
	@DisplayName("백오피스 계정은 이메일 표기를 바꿔 시도해도 한 카운터에 모이고 30분 차단 TTL이 걸린다")
	void login_whenBackofficeAccountWithMixedNotation_thenSharesKeyWithThirtyMinuteTtl() {
		// given
		when(commonAuthRepository.findRoleByEmail(EMAIL)).thenReturn(Optional.of(UserRole.BACKOFFICE));
		List<String> notations = List.of(EMAIL, " USER@assu.com", "User@Assu.Com ", "user@ASSU.COM", EMAIL);

		// when
		for (String notation : notations) {
			assertDoesNotThrow(() -> loginAttemptLimiter.checkAttempt(notation));
		}

		// then
		assertThrows(CustomAuthException.class, () -> loginAttemptLimiter.checkAttempt("USER@assu.com"));
		assertEquals("6", redisTemplate.opsForValue().get(LOGIN_KEY));
		long ttl = ttlSeconds(LOGIN_KEY);
		assertTrue(ttl > 1790 && ttl <= 1800, "ttl=" + ttl);
	}

	@Test
	@DisplayName("5번째 이후 요청은 차단 TTL을 연장하지 않고, 초기화하면 키가 사라진다")
	void login_whenBlockedRequestsContinue_thenTtlIsNotExtendedAndResetClearsKey() {
		// given
		when(commonAuthRepository.findRoleByEmail(EMAIL)).thenReturn(Optional.empty());
		for (int i = 0; i < 5; i++) {
			loginAttemptLimiter.checkAttempt(EMAIL);
		}
		redisTemplate.expire(LOGIN_KEY, Duration.ofSeconds(100));

		// when
		for (int i = 0; i < 3; i++) {
			assertThrows(CustomAuthException.class, () -> loginAttemptLimiter.checkAttempt(EMAIL));
		}

		// then
		assertTrue(ttlSeconds(LOGIN_KEY) <= 100);
		loginAttemptLimiter.reset(EMAIL);
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(LOGIN_KEY)));
	}

	@Test
	@DisplayName("백오피스 계정을 공통 로그인과 백오피스 로그인으로 번갈아 시도하면 5번째·6번째가 어느 경로로 오든 30분 차단이 걸리고 6번째는 막힌다")
	void login_whenAlternatingCommonAndBackofficeEndpoints_thenSharesBudgetWithBackofficeBlock() {
		for (boolean fifthViaCommon : new boolean[] {true, false}) {
			for (boolean sixthViaCommon : new boolean[] {true, false}) {
				// given
				redisTemplate.delete(LOGIN_KEY);
				when(commonAuthRepository.findRoleByEmail(EMAIL)).thenReturn(Optional.of(UserRole.BACKOFFICE));
				AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
				when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
				LoginServiceImpl commonLogin = new LoginServiceImpl(authenticationManager, mock(JwtUtil.class),
					mock(StudentRepository.class), new SimpleMeterRegistry(), loginAttemptLimiter, List.of(),
					mock(TransactionTemplate.class));
				BackofficeAuthServiceImpl backofficeLogin = new BackofficeAuthServiceImpl(authenticationManager,
					mock(JwtUtil.class), loginAttemptLimiter, List.of());
				CommonLoginRequestDTO request = new CommonLoginRequestDTO(EMAIL, "wrong-password1!");

				// when
				for (int attempt = 1; attempt <= 5; attempt++) {
					boolean viaCommon = attempt == 5 ? fifthViaCommon : attempt % 2 == 1;
					if (viaCommon) {
						assertThrows(BadCredentialsException.class, () -> commonLogin.loginCommon(request));
					} else {
						assertThrows(BadCredentialsException.class, () -> backofficeLogin.login(request));
					}
				}
				long ttl = ttlSeconds(LOGIN_KEY);
				CustomAuthException sixth = sixthViaCommon
					? assertThrows(CustomAuthException.class, () -> commonLogin.loginCommon(request))
					: assertThrows(CustomAuthException.class, () -> backofficeLogin.login(request));

				// then
				String scenario = "fifthViaCommon=" + fifthViaCommon + " sixthViaCommon=" + sixthViaCommon;
				assertTrue(ttl > 1790 && ttl <= 1800, scenario + " ttl=" + ttl);
				assertEquals(ErrorStatus.LOGIN_ATTEMPTS_EXCEEDED, sixth.getCode(), scenario);
			}
		}
	}

	// ===== 인증번호 검증 =====

	@Test
	@DisplayName("인증번호를 5번 틀리면 폐기되어 이후 정답을 넣어도 인증되지 않는다")
	void verify_whenFiveMismatches_thenCodeIsDiscardedAndCorrectCodeFails() {
		// given
		redisTemplate.opsForValue().set(CODE_KEY, "111111", Duration.ofMinutes(5));

		// when
		for (int i = 0; i < 4; i++) {
			assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, verifyResult("000000"));
		}
		assertEquals(ErrorStatus.PHONE_AUTH_ATTEMPTS_EXCEEDED, verifyResult("000000"));

		// then
		assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, verifyResult("111111"));
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(CODE_KEY)));
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(FAIL_KEY)));
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(VERIFIED_KEY)));
	}

	@Test
	@DisplayName("틀린 인증번호를 동시에 20번 보내도 4번만 비교되고 5번째에 폐기되며 나머지는 비교 없이 거절된다")
	void verify_whenConcurrentMismatches_thenOnlyFiveAttemptsAreCompared() throws Exception {
		// given
		redisTemplate.opsForValue().set(CODE_KEY, "111111", Duration.ofMinutes(5));
		int requests = 20;
		ExecutorService executor = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ErrorStatus>> results = new ArrayList<>();
		for (int i = 0; i < requests; i++) {
			Callable<ErrorStatus> task = () -> {
				start.await();
				return verifyResult("000000");
			};
			results.add(executor.submit(task));
		}

		// when
		start.countDown();
		int exhausted = 0;
		int notVerified = 0;
		for (Future<ErrorStatus> result : results) {
			ErrorStatus status = result.get();
			if (status == ErrorStatus.PHONE_AUTH_ATTEMPTS_EXCEEDED) {
				exhausted++;
			} else if (status == ErrorStatus.NOT_VERIFIED_PHONE_NUMBER) {
				notVerified++;
			}
		}
		executor.shutdown();

		// then
		assertEquals(1, exhausted);
		assertEquals(requests - 1, notVerified);
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(CODE_KEY)));
		assertEquals(ErrorStatus.NOT_VERIFIED_PHONE_NUMBER, verifyResult("111111"));
	}

	@Test
	@DisplayName("인증에 성공하면 인증번호와 실패 횟수가 지워지고 인증 표식이 30분 TTL로 저장되어 한 번만 소비된다")
	void verify_whenCorrect_thenMarkerStoredOnceAndConsumedOnce() {
		// given
		redisTemplate.opsForValue().set(CODE_KEY, "111111", Duration.ofMinutes(5));
		verifyResult("000000");

		// when
		assertNull(verifyResult("111111"));

		// then
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(CODE_KEY)));
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(FAIL_KEY)));
		long ttl = ttlSeconds(VERIFIED_KEY);
		assertTrue(ttl > 1790 && ttl <= 1800, "ttl=" + ttl);
		assertDoesNotThrow(() -> phoneAuthService.consumeVerification(PHONE));
		assertThrows(CustomAuthException.class, () -> phoneAuthService.consumeVerification(PHONE));
	}

	@Test
	@DisplayName("가입이 롤백되어 인증 표식을 복원하면 다시 한 번 소비할 수 있다")
	void restoreVerification_afterConsume_thenConsumableAgainOnce() {
		// given
		redisTemplate.opsForValue().set(VERIFIED_KEY, "1", Duration.ofMinutes(30));
		phoneAuthService.consumeVerification(PHONE);

		// when
		phoneAuthService.restoreVerification(PHONE);

		// then
		assertTrue(ttlSeconds(VERIFIED_KEY) > 0);
		assertDoesNotThrow(() -> phoneAuthService.consumeVerification(PHONE));
		assertThrows(CustomAuthException.class, () -> phoneAuthService.consumeVerification(PHONE));
	}

	// ===== 인증번호 발송 =====

	@Test
	@DisplayName("발송에 성공하면 횟수 1·쿨다운·인증번호가 저장되고 60초 안의 재요청은 쿨다운으로 거절된다")
	void send_whenSucceeded_thenReservesAndSecondRequestHitsCooldown() {
		// given
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString())).thenReturn(aligoResponse("1"));
		redisTemplate.opsForValue().set(FAIL_KEY, "3");

		// when
		phoneAuthService.checkAndSendAuthNumber(PHONE);
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals(ErrorStatus.PHONE_AUTH_SEND_COOLDOWN, exception.getCode());
		assertEquals("1", redisTemplate.opsForValue().get(COUNT_KEY));
		assertTrue(ttlSeconds(COUNT_KEY) > 86390);
		assertEquals(ttlSeconds(COUNT_KEY), ttlSeconds(WINDOW_KEY), 1);
		assertTrue(ttlSeconds(COOLDOWN_KEY) > 50);
		assertNotNull(redisTemplate.opsForValue().get(CODE_KEY));
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(FAIL_KEY)));
	}

	@Test
	@DisplayName("발송이 실패하면 이 요청의 횟수·쿨다운·인증번호를 되돌려 바로 다시 요청할 수 있다")
	void send_whenFailed_thenReleasesReservation() {
		// given
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString()))
			.thenReturn(aligoResponse("-101"))
			.thenReturn(aligoResponse("1"));

		// when
		assertThrows(CustomAuthException.class, () -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals("0", redisTemplate.opsForValue().get(COUNT_KEY));
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY)));
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(CODE_KEY)));
		assertDoesNotThrow(() -> phoneAuthService.checkAndSendAuthNumber(PHONE));
		assertEquals("1", redisTemplate.opsForValue().get(COUNT_KEY));
	}

	@Test
	@DisplayName("24시간 동안 10번 발송한 번호는 거절되고 쿨다운도 만들지 않는다")
	void send_whenLimitReached_thenRejectsWithoutCooldown() {
		// given
		redisTemplate.opsForValue().set(COUNT_KEY, "10", Duration.ofHours(1));

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals(ErrorStatus.PHONE_AUTH_SEND_LIMIT_EXCEEDED, exception.getCode());
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY)));
		assertEquals("10", redisTemplate.opsForValue().get(COUNT_KEY));
	}

	@Test
	@DisplayName("발송이 쿨다운보다 오래 걸린 사이 다른 요청이 같은 창에 예약했다면, 늦은 실패는 자기 횟수만 되돌리고 상대 쿨다운·인증번호는 남긴다")
	void send_whenLateFailureAfterAnotherReservationInSameWindow_thenReleasesOnlyOwnShare() {
		// given
		AtomicInteger calls = new AtomicInteger();
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString())).thenAnswer(invocation -> {
			if (calls.incrementAndGet() == 1) {
				redisTemplate.delete(COOLDOWN_KEY);
				phoneAuthService.checkAndSendAuthNumber(PHONE);
				throw new IllegalStateException("timeout");
			}
			return aligoResponse("1");
		});

		// when
		assertThrows(IllegalStateException.class, () -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals("1", redisTemplate.opsForValue().get(COUNT_KEY));
		assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY)));
		assertNotNull(redisTemplate.opsForValue().get(CODE_KEY));
	}

	@Test
	@DisplayName("쿨다운 만료 후 다른 예약 없이 늦게 실패해도 같은 창이면 횟수를 되돌린다")
	void send_whenLateFailureWithoutOtherReservation_thenRestoresCount() {
		// given
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString())).thenAnswer(invocation -> {
			redisTemplate.delete(COOLDOWN_KEY);
			throw new IllegalStateException("timeout");
		});

		// when
		assertThrows(IllegalStateException.class, () -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals("0", redisTemplate.opsForValue().get(COUNT_KEY));
	}

	@Test
	@DisplayName("창이 만료되고 새 창에 다른 예약이 생긴 뒤의 늦은 실패는 새 창의 횟수를 줄이지 않는다")
	void send_whenLateFailureAfterWindowRollover_thenNewWindowCountIsKept() {
		// given
		AtomicInteger calls = new AtomicInteger();
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString())).thenAnswer(invocation -> {
			if (calls.incrementAndGet() == 1) {
				redisTemplate.delete(List.of(COOLDOWN_KEY, COUNT_KEY, WINDOW_KEY));
				phoneAuthService.checkAndSendAuthNumber(PHONE);
				throw new IllegalStateException("timeout");
			}
			return aligoResponse("1");
		});

		// when
		assertThrows(IllegalStateException.class, () -> phoneAuthService.checkAndSendAuthNumber(PHONE));

		// then
		assertEquals("1", redisTemplate.opsForValue().get(COUNT_KEY));
		assertTrue(ttlSeconds(COUNT_KEY) > 86390);
	}

	@Test
	@DisplayName("횟수가 0으로 되돌아간 뒤 다시 예약해도 집계 창 만료는 늘어나지 않는다")
	void send_whenReservedAgainAfterCountReturnedToZero_thenWindowTtlIsKept() {
		// given
		when(aligoSmsClient.sendSms(eq(PHONE), anyString(), anyString()))
			.thenReturn(aligoResponse("-101"))
			.thenReturn(aligoResponse("1"));
		assertThrows(CustomAuthException.class, () -> phoneAuthService.checkAndSendAuthNumber(PHONE));
		redisTemplate.expire(COUNT_KEY, Duration.ofSeconds(100));
		redisTemplate.expire(WINDOW_KEY, Duration.ofSeconds(100));

		// when
		phoneAuthService.checkAndSendAuthNumber(PHONE);

		// then
		assertEquals("1", redisTemplate.opsForValue().get(COUNT_KEY));
		assertTrue(ttlSeconds(COUNT_KEY) <= 100);
		assertTrue(ttlSeconds(WINDOW_KEY) <= 100);
	}

	// ===== IP별 요청 상한 =====

	@Test
	@DisplayName("같은 IPv6 주소를 다른 표기로 보내도 한 한도를 나눠 쓰며 31번째 요청이 차단된다")
	void ipLimit_whenSameIpv6InDifferentNotations_thenSharesOneLimit() {
		// given
		IpRateLimitInterceptor interceptor =
			new IpRateLimitInterceptor(redisTemplate, new SimpleMeterRegistry(), "login", 30, Duration.ofMinutes(15));

		// when
		for (int i = 0; i < 30; i++) {
			String notation = i % 2 == 0 ? "2606:4700:4700::1111" : "2606:4700:4700:0:0:0:0:1111";
			assertTrue(interceptor.preHandle(post(notation), new MockHttpServletResponse(), new Object()));
		}

		// then
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> interceptor.preHandle(post("2606:4700:4700::1111"), new MockHttpServletResponse(), new Object()));
		assertEquals(ErrorStatus.AUTH_REQUESTS_EXCEEDED, exception.getCode());
	}

	private static MockHttpServletRequest post(String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/commons/login");
		request.addHeader("X-Forwarded-For", forwardedFor);
		return request;
	}
}
