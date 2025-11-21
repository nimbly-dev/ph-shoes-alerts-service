package com.nimbly.phshoesbackend.alerts.core.util;

import com.nimbly.phshoesbackend.alerts.core.exception.InvalidAlertException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class AlertValidationUtils {
    private AlertValidationUtils() {}

    public static void validateTriggers(BigDecimal desiredPrice, BigDecimal desiredPercent, Boolean alertIfSale) {
        boolean hasPrice = desiredPrice != null && desiredPrice.compareTo(BigDecimal.ZERO) > 0;
        boolean hasPercent = desiredPercent != null && desiredPercent.compareTo(BigDecimal.ZERO) > 0;

        boolean hasSaleToggle = Boolean.TRUE.equals(alertIfSale);
        if (!hasPrice && !hasPercent && !hasSaleToggle) {
            throw new InvalidAlertException("At least one trigger must be provided");
        }
    }

    public static void validatePricingSnapshot(BigDecimal desiredPercent, BigDecimal originalPrice, BigDecimal currentPrice) {
        if (desiredPercent != null && desiredPercent.compareTo(BigDecimal.ZERO) > 0) {
            if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidAlertException("productOriginalPrice is required when desiredPercent is set");
            }
        }
        if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAlertException("productCurrentPrice, if provided, must be > 0");
        }
    }

    public static List<String> normalizeChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of("APP_WIDGET");
        }
        var out = new ArrayList<String>();
        for (String c : channels) {
            if (c == null) continue;
            var cleaned = c.trim().toUpperCase();
            if (!cleaned.isEmpty() && !out.contains(cleaned)) {
                out.add(cleaned);
            }
        }
        if (out.isEmpty()) {
            out.add("APP_WIDGET");
        }
        return out;
    }
}
