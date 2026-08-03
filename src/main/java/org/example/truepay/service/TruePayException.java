package org.example.truepay.service;

import org.example.truepay.model.ErrorCode;
import org.springframework.http.HttpStatus;

public class TruePayException extends RuntimeException {
    private final ErrorCode errorCode;
    private final HttpStatus status;

    public TruePayException(ErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

