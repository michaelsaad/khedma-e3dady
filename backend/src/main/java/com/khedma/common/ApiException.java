package com.khedma.common;

import org.springframework.http.HttpStatus;

/** Business error carrying a stable {@link ErrorCode}; the HTTP status comes from the code. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return code.status();
    }
}
