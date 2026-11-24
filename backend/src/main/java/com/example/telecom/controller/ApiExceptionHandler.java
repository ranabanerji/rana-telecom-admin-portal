package com.example.telecom.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import java.sql.SQLException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(FieldProblem.class)
    public ProblemDetail fieldProblem(FieldProblem exception) {
        return problem(exception.getStatus(), exception.getField(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the highlighted fields");
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail parameter(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getName(), "Invalid value for " + exception.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "body", "Invalid JSON request or field type");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException exception) {
        return conflict(exception.getMostSpecificCause().getMessage());
    }

    // SQLite's JDBC driver reports constraint code 19 without a SQLState, which
    // Hibernate can wrap as JpaSystemException rather than
    // DataIntegrityViolationException.
    @ExceptionHandler(JpaSystemException.class)
    public ProblemDetail sqliteIntegrity(JpaSystemException exception) {
        if (exception.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == 19) {
            return conflict(sql.getMessage());
        }
        throw exception;
    }

    private static ProblemDetail conflict(String cause) {
        String field = "dependencies";
        for (String unique : new String[] {"email", "imei", "sim_number"}) {
            if (cause != null && cause.contains(unique)) {
                field = unique;
                break;
            }
        }
        return problem(HttpStatus.CONFLICT, field, "Cannot save or delete: " + field
                + " conflicts with existing records. Reassign dependents or deactivate instead.");
    }

    private static ProblemDetail problem(HttpStatus status, String field, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setProperty("errors", Map.of(field, message));
        return detail;
    }
}
