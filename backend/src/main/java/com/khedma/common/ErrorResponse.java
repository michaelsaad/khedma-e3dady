package com.khedma.common;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String message, Map<String, String> fieldErrors) {

    public ErrorResponse(String message) {
        this(message, Map.of());
    }
}
