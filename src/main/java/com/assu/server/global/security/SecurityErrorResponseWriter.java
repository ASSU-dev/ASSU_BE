package com.assu.server.global.security;

import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorStatus status)
            throws IOException {
        response.setStatus(status.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        BaseResponse<Object> body = BaseResponse.onFailure(status.getCode(), status.getMessage(), null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
