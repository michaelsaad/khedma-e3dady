package com.khedma.common;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error body: {@code code} is what clients should switch on; {@code message} is an English fallback.
 * For validation errors, each field error carries the constraint name (e.g. NotBlank, Size) as its code.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(ErrorCode code, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String code, String message) {}

    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(code, code.defaultMessage(), List.of());
    }
}
