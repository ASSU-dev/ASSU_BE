package com.assu.server.global.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogMaskUtilTest {

	@Test
	@DisplayName("앞 4자리만 남기고 나머지를 가린다")
	void mask_KeepsOnlyFirstFourCharacters() {
		assertEquals("Vy3z...[MASKED]", LogMaskUtil.mask("Vy3zSECRET"));
	}

	@Test
	@DisplayName("4자 이하 값은 전부 가리고, null은 문자열 null로 남긴다")
	void mask_ShortOrNullValue() {
		assertEquals("[MASKED]", LogMaskUtil.mask("abcd"));
		assertEquals("null", LogMaskUtil.mask(null));
	}
}
