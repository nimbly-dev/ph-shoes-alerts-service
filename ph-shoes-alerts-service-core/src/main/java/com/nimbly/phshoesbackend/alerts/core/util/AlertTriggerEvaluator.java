package com.nimbly.phshoesbackend.alerts.core.util;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertProductSnapshot;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public final class AlertTriggerEvaluator {

    public static TriggerDecision evaluate(Alert alert, AlertProductSnapshot snapshot) {
        BigDecimal sale = firstNonNull(snapshot.getPriceSale(), snapshot.getPriceOriginal());
        BigDecimal original = firstNonNull(snapshot.getPriceOriginal(), sale);
        boolean onSale = sale != null && original != null && sale.compareTo(original) < 0;

        if (alert.getDesiredPrice() != null && sale != null && sale.compareTo(alert.getDesiredPrice()) <= 0) {
            return new TriggerDecision(true, "price<=desired");
        }

        if (alert.getDesiredPercent() != null && sale != null && original != null && original.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal drop = original.subtract(sale)
                    .divide(original, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (drop.compareTo(alert.getDesiredPercent()) >= 0) {
                return new TriggerDecision(true, "drop>=" + alert.getDesiredPercent() + "%");
            }
        }

        if (Boolean.TRUE.equals(alert.getAlertIfSale()) && onSale) {
            return new TriggerDecision(true, "on-sale");
        }

        return TriggerDecision.NOT_TRIGGERED;
    }

    public static void applyTriggeredAlert(Alert alert, AlertProductSnapshot snapshot, Instant triggeredAt) {
        BigDecimal sale = firstNonNull(snapshot.getPriceSale(), snapshot.getPriceOriginal());
        BigDecimal original = firstNonNull(snapshot.getPriceOriginal(), sale);

        alert.setStatus(AlertStatus.TRIGGERED);
        alert.setLastTriggeredAt(triggeredAt);
        alert.setUpdatedAt(triggeredAt);
        if (sale != null) {
            alert.setProductCurrentPrice(sale);
        }
        if (original != null) {
            alert.setProductOriginalPrice(original);
        }
        if (snapshot.getProductName() != null) {
            alert.setProductName(snapshot.getProductName());
        }
        if (snapshot.getProductBrand() != null) {
            alert.setProductBrand(snapshot.getProductBrand());
        }
        if (snapshot.getProductImage() != null) {
            alert.setProductImage(snapshot.getProductImage());
        }
        if (snapshot.getProductImageUrl() != null) {
            alert.setProductImageUrl(snapshot.getProductImageUrl());
        }
        if (snapshot.getProductUrl() != null) {
            alert.setProductUrl(snapshot.getProductUrl());
        }
    }

    private static BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }

    public record TriggerDecision(boolean triggered, String reason) {
        public static final TriggerDecision NOT_TRIGGERED = new TriggerDecision(false, null);
    }
}
