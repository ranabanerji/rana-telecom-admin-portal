package com.example.telecom.controller;

import org.springframework.http.HttpStatus;

public class FieldProblem extends RuntimeException {
    private final String field;
    private final HttpStatus status;

    public FieldProblem(String field, String message) {
        this(field, message, HttpStatus.BAD_REQUEST);
    }

    public FieldProblem(String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
        this.status = HttpStatus.BAD_REQUEST;
    }

    public FieldProblem(String field, String message, HttpStatus status) {
        super(message);
        this.field = field;
        this.status = status;
    }

    public String getField() {
        return field;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
