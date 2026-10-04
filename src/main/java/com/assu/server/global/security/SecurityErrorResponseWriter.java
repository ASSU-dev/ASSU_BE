package com.assu.server.global.security;

import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.ErrorReasonDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

public final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorReasonDTO reason)
            throws IOException {
        response.setStatus(reason.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        BaseResponse<Object> body = BaseResponse.onFailure(reason.getCode(), reason.getMessage(), null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
