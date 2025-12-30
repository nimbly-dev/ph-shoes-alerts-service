package com.nimbly.phshoesbackend.alerts.web;

import com.nimbly.phshoesbackend.alerts.core.exception.AlertNotFoundException;
import com.nimbly.phshoesbackend.alerts.core.exception.DuplicateAlertException;
import com.nimbly.phshoesbackend.alerts.core.exception.InvalidAlertException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fe.getField(), k -> new ArrayList<>())
                    .add(fe.getDefaultMessage());
        }
        return Map.of(
                "code", "VALIDATION_ERROR",
                "details", errors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleConstraintViolation(ConstraintViolationException ex) {
        return Map.of(
                "code", "VALIDATION_ERROR",
                "details", ex.getMessage()
        );
    }

    @ExceptionHandler(AlertNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(AlertNotFoundException ex) {
        return Map.of("code", "NOT_FOUND", "message", ex.getMessage());
    }

    @ExceptionHandler(DuplicateAlertException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleDuplicate(DuplicateAlertException ex) {
        return Map.of("code", "ALREADY_EXISTS", "message", ex.getMessage());
    }

    @ExceptionHandler(InvalidAlertException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalid(InvalidAlertException ex) {
        return Map.of("code", "INVALID_ALERT", "message", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneric(Exception ex) {
        return Map.of("code", "INTERNAL_ERROR", "message", ex.getMessage());
    }
}
