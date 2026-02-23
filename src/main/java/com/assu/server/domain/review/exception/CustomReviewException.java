package com.assu.server.domain.review.exception;

import com.assu.server.global.apiPayload.code.BaseErrorCode;
import com.assu.server.global.exception.GeneralException;

public class CustomReviewException extends GeneralException {

    public CustomReviewException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
