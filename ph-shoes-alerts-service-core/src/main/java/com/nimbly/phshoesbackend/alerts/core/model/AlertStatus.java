package com.nimbly.phshoesbackend.alerts.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AlertStatus {
    ACTIVE("ACTIVE"),
    TRIGGERED("TRIGGERED"),
    PAUSED("PAUSED");

    private final String value;

    AlertStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @JsonCreator
    public static AlertStatus fromValue(String value) {
        for (AlertStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "' for AlertStatus");
    }
}
