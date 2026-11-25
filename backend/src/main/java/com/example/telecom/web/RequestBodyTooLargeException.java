package com.example.telecom.web;

import java.io.IOException;

/**
 * Thrown while reading a request body that has grown past the configured limit.
 */
public class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException(String message) {
        super(message);
    }
}
