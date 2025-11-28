package com.nimbly.phshoesbackend.alerts.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AlertChannel {
    APP_WIDGET("APP_WIDGET"),
    EMAIL("EMAIL");

    private final String value;

    AlertChannel(String value) {
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
    public static AlertChannel fromValue(String value) {
        for (AlertChannel channel : values()) {
            if (channel.value.equalsIgnoreCase(value)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "' for AlertChannel");
    }
}
