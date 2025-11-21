package com.nimbly.phshoesbackend.alerts.core.exception;

public class DuplicateAlertException extends RuntimeException {
    public DuplicateAlertException(String message, Throwable cause) {
        super(message, cause);
    }
}
