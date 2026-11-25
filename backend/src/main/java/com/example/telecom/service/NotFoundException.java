package com.example.telecom.service;

/** The record a request is addressed to does not exist. */
public class NotFoundException extends ServiceException {
    public NotFoundException(String entity, Long id) {
        super("id", entity + " " + id + " was not found");
    }
}
