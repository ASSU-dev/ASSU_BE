package com.assu.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

@ExtendWith(OutputCaptureExtension.class)
class SSUAuthServiceImplTest {

	private static final String S_TOKEN = "Vy3zFySECRETTOKENVALUE0123456789";
	private static final String S_IDNO = "20211438";

	private SSUAuthServiceImpl serviceWith(ExchangeFunction exchangeFunction) {
		WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
		return new SSUAuthServiceImpl(webClient, new SimpleMeterRegistry());
	}

	@Test
	@DisplayName("SSO 요청이 HTTP 오류로 실패해도 로그에 sToken과 학번이 평문으로 남지 않는다")
	void uSaintAuth_SsoHttpError_DoesNotLogCredentials(CapturedOutput output) {
		// given
		SSUAuthServiceImpl service = serviceWith(request ->
			Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

		// when
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> service.uSaintAuth(new USaintAuthRequestDTO(S_TOKEN, S_IDNO)));

		// then
		assertEquals(ErrorStatus.SSU_SAINT_SSO_FAILED, exception.getCode());
		assertThat(output.getAll()).contains("HTTP 500");
		assertThat(output.getAll()).doesNotContain(S_TOKEN).doesNotContain(S_IDNO);
	}

	@Test
	@DisplayName("SSO 응답이 로그인 성공 형태가 아니면 sToken과 학번을 마스킹해 로그에 남긴다")
	void uSaintAuth_InvalidSsoResponse_LogsMaskedCredentials(CapturedOutput output) {
		// given
		SSUAuthServiceImpl service = serviceWith(request ->
			Mono.just(ClientResponse.create(HttpStatus.OK).body("<html>login failed</html>").build()));

		// when
		assertThrows(CustomAuthException.class,
			() -> service.uSaintAuth(new USaintAuthRequestDTO(S_TOKEN, S_IDNO)));

		// then
		assertThat(output.getAll()).contains("sToken=Vy3z...[MASKED]").contains("sIdno=2021...[MASKED]");
		assertThat(output.getAll()).doesNotContain(S_TOKEN).doesNotContain(S_IDNO);
	}

	@Test
	@DisplayName("SSO 요청은 기존과 같이 쿼리와 쿠키 양쪽에 sToken, sIdno를 담아 보낸다")
	void uSaintAuth_SendsTokenInQueryAndCookie() {
		// given
		AtomicReference<ClientRequest> captured = new AtomicReference<>();
		SSUAuthServiceImpl service = serviceWith(request -> {
			captured.compareAndSet(null, request);
			return Mono.just(ClientResponse.create(HttpStatus.OK).body("<html></html>").build());
		});

		// when
		assertThrows(CustomAuthException.class,
			() -> service.uSaintAuth(new USaintAuthRequestDTO(S_TOKEN, S_IDNO)));

		// then
		URI uri = captured.get().url();
		assertEquals("saint.ssu.ac.kr", uri.getHost());
		assertEquals("sToken=" + S_TOKEN + "&sIdno=" + S_IDNO, uri.getQuery());
		assertEquals("sToken=" + S_TOKEN + "; sIdno=" + S_IDNO,
			captured.get().headers().getFirst(HttpHeaders.COOKIE));
	}
}
