package com.assu.server.domain.store.exception;

import com.assu.server.global.apiPayload.code.BaseErrorCode;
import com.assu.server.global.exception.GeneralException;

public class CustomStoreException extends GeneralException {

    public CustomStoreException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}