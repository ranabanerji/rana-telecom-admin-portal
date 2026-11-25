package com.example.telecom.service;

/**
 * An input is missing, malformed or refers to something that does not exist.
 */
public class InvalidInputException extends ServiceException {
    public InvalidInputException(String field, String message) {
        super(field, message);
    }
}
