package com.nimbly.phshoesbackend.alerts.core.model;

public record EmailDeliveryReport(int sent, int suppressed, int errors) {
}
