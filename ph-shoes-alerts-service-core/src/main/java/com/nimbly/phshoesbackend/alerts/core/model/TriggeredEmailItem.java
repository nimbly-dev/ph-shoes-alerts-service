package com.nimbly.phshoesbackend.alerts.core.model;

public record TriggeredEmailItem(AlertProductSnapshot snapshot, String reason) {
}
