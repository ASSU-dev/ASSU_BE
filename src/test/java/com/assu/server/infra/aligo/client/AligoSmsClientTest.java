package com.assu.server.infra.aligo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.aligo.dto.AligoSendResponse;
import com.assu.server.infra.aligo.exception.AligoException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

class AligoSmsClientTest {

	private static final String API_KEY = "test-api-key";
	private static final String USER_ID = "test-user-id";
	private static final String SENDER = "0212345678";
	private static final String RECEIVER = "01012345678";
	private static final String MESSAGE = "[ASSU] 인증번호: 123456";

	private final AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();

	private AligoSmsClient buildClient(HttpStatus status, String responseBody, MediaType contentType) {
		ExchangeFunction exchangeFunction = request -> {
			capturedRequest.set(request);
			ClientResponse.Builder builder = ClientResponse.create(status);
			if (contentType != null) {
				builder.header(HttpHeaders.CONTENT_TYPE, contentType.toString());
			}
			return Mono.just(builder.body(responseBody).build());
		};

		WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
		AligoSmsClient client = new AligoSmsClient(webClient, new ObjectMapper());
		ReflectionTestUtils.setField(client, "apiKey", API_KEY);
		ReflectionTestUtils.setField(client, "userId", USER_ID);
		ReflectionTestUtils.setField(client, "sender", SENDER);
		return client;
	}

	private String capturedFormBody() {
		ClientRequest request = capturedRequest.get();
		assertNotNull(request, "요청이 전송되지 않았습니다.");

		MockClientHttpRequest mockRequest = new MockClientHttpRequest(HttpMethod.POST, "/");
		ExchangeStrategies strategies = ExchangeStrategies.withDefaults();

		request.body().insert(mockRequest, new BodyInserter.Context() {
			@Override
			public List<HttpMessageWriter<?>> messageWriters() {
				return strategies.messageWriters();
			}

			@Override
			public Optional<ServerHttpRequest> serverRequest() {
				return Optional.empty();
			}

			@Override
			public Map<String, Object> hints() {
				return Collections.emptyMap();
			}
		}).block();

		return mockRequest.getBodyAsString().block();
	}

	@BeforeEach
	void resetCapture() {
		capturedRequest.set(null);
	}

	@Test
	@DisplayName("알리고 규격대로 사용자 ID를 user_id 파라미터로 전송한다")
	void sendSms_SendsUserIdWithSpecCompliantParameterName() {
		// 1. Given
		AligoSmsClient client = buildClient(
			HttpStatus.OK,
			"{\"result_code\":1,\"message\":\"success\",\"msg_id\":123,\"success_cnt\":1,\"error_cnt\":0,\"msg_type\":\"SMS\"}",
			MediaType.APPLICATION_JSON);

		// 2. When
		client.sendSms(RECEIVER, MESSAGE, "사용자");

		// 3. Then
		String body = capturedFormBody();
		assertTrue(body.contains("user_id=" + USER_ID), "알리고 규격 파라미터명은 user_id 입니다. 실제 전송 body: " + body);
		assertFalse(body.contains("userid="), "규격에 없는 userid 파라미터가 전송되었습니다. 실제 전송 body: " + body);
	}

	@Test
	@DisplayName("알리고 필수 파라미터가 폼 데이터로 모두 전송된다")
	void sendSms_SendsAllRequiredParameters() {
		// 1. Given
		AligoSmsClient client = buildClient(
			HttpStatus.OK,
			"{\"result_code\":1,\"message\":\"success\"}",
			MediaType.APPLICATION_JSON);

		// 2. When
		client.sendSms(RECEIVER, MESSAGE, "사용자");

		// 3. Then
		String body = capturedFormBody();
		assertTrue(body.contains("key=" + API_KEY), body);
		assertTrue(body.contains("sender=" + SENDER), body);
		assertTrue(body.contains("receiver=" + RECEIVER), body);
		assertTrue(body.contains("msg_type=SMS"), body);
		assertTrue(body.contains("msg="), body);

		assertEquals(MediaType.APPLICATION_FORM_URLENCODED, capturedRequest.get().headers().getContentType());
	}

	@Test
	@DisplayName("result_code가 숫자 타입인 알리고 성공 응답을 파싱한다")
	void sendSms_ParsesNumericResultCodeResponse() {
		// 1. Given
		AligoSmsClient client = buildClient(
			HttpStatus.OK,
			"{\"result_code\":1,\"message\":\"success\",\"msg_id\":123,\"success_cnt\":1,\"error_cnt\":0,\"msg_type\":\"SMS\"}",
			MediaType.APPLICATION_JSON);

		// 2. When
		AligoSendResponse response = client.sendSms(RECEIVER, MESSAGE, "사용자");

		// 3. Then
		assertEquals("1", response.getResult_code());
		assertEquals("success", response.getMessage());
	}

	@Test
	@DisplayName("응답에 규격 외 필드가 포함되어도 파싱에 실패하지 않는다")
	void sendSms_IgnoresUnknownResponseFields() {
		// 1. Given
		AligoSmsClient client = buildClient(
			HttpStatus.OK,
			"{\"result_code\":-101,\"message\":\"인증오류입니다.\",\"unknown_field\":\"x\"}",
			MediaType.APPLICATION_JSON);

		// 2. When
		AligoSendResponse response = client.sendSms(RECEIVER, MESSAGE, "사용자");

		// 3. Then
		assertEquals("-101", response.getResult_code());
	}

	@Test
	@DisplayName("HTTP 에러 응답의 body가 비어 있어도 SMS 전송 실패 예외가 발생한다")
	void sendSms_EmptyErrorBody_ThrowsSendFailure() {
		// 1. Given
		AligoSmsClient client = buildClient(HttpStatus.INTERNAL_SERVER_ERROR, "", null);

		// 2. When
		AligoException exception = assertThrows(AligoException.class,
			() -> client.sendSms(RECEIVER, MESSAGE, "사용자"));

		// 3. Then
		assertEquals(ErrorStatus.FAILED_TO_SEND_SMS, exception.getCode());
	}

	@Test
	@DisplayName("응답 본문이 JSON이 아니면 파싱 실패 예외가 발생한다")
	void sendSms_NonJsonBody_ThrowsParseFailure() {
		// 1. Given
		AligoSmsClient client = buildClient(HttpStatus.OK, "<html>error</html>", MediaType.TEXT_HTML);

		// 2. When
		AligoException exception = assertThrows(AligoException.class,
			() -> client.sendSms(RECEIVER, MESSAGE, "사용자"));

		// 3. Then
		assertEquals(ErrorStatus.FAILED_TO_PARSE_ALIGO, exception.getCode());
	}
}
