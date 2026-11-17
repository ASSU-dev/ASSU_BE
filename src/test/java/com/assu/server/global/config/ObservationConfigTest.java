package com.assu.server.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;

import java.net.URI;

class ObservationConfigTest {

	private static final String HTTP_URL = "http.url";

	private final ObservationFilter filter = new ObservationConfig().usaintUrlQueryStripFilter();

	private ClientRequestObservationContext contextWithUrl(String url) {
		ClientRequestObservationContext context =
			new ClientRequestObservationContext(ClientRequest.create(HttpMethod.GET, URI.create(url)));
		context.addHighCardinalityKeyValue(KeyValue.of(HTTP_URL, url));
		return context;
	}

	@Test
	@DisplayName("유세인트 요청의 http.url 속성에서 쿼리(sToken)를 제거한다")
	void usaintUrlQueryStripFilter_StripsQueryFromUSaintUrl() {
		// given
		ClientRequestObservationContext context =
			contextWithUrl("https://saint.ssu.ac.kr/webSSO/sso.jsp?sToken=SECRET&sIdno=20211438");

		// when
		filter.map(context);

		// then
		assertEquals("https://saint.ssu.ac.kr/webSSO/sso.jsp",
			context.getHighCardinalityKeyValue(HTTP_URL).getValue());
	}

	@Test
	@DisplayName("유세인트가 아닌 요청의 http.url 속성은 그대로 둔다")
	void usaintUrlQueryStripFilter_KeepsOtherUrls() {
		// given
		String url = "https://dapi.kakao.com/v2/local/search/keyword.json?query=cafe";
		ClientRequestObservationContext context = contextWithUrl(url);

		// when
		filter.map(context);

		// then
		assertEquals(url, context.getHighCardinalityKeyValue(HTTP_URL).getValue());
	}
}
