package com.example.telecom.service;

/** The change would break a rule that depends on other records. */
public class ConflictException extends ServiceException {
    public ConflictException(String field, String message) {
        super(field, message);
    }
}
