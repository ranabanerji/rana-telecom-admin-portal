package com.example.telecom.service;

/**
 * A request broke a business rule. {@link #getField()} names the input that
 * caused it so callers can point at the offending field.
 */
public class ServiceException extends RuntimeException {
    private final String field;

    protected ServiceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
