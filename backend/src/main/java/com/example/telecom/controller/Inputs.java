package com.example.telecom.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.DateTimeException;

final class Inputs {
    private Inputs() {
    }

    static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new FieldProblem(field, "Invalid " + field + ": " + value, exception);
        }
    }

    static Instant date(String value, String field, boolean exclusiveEnd) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            LocalDate day = LocalDate.parse(value);
            return (exclusiveEnd ? day.plusDays(1) : day).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeException exception) {
            throw new FieldProblem(field, field + " must be a valid ISO date (YYYY-MM-DD)", exception);
        }
    }
}
