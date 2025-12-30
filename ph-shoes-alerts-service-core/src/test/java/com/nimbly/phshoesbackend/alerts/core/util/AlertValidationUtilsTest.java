package com.nimbly.phshoesbackend.alerts.core.util;

import com.nimbly.phshoesbackend.alerts.core.exception.InvalidAlertException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertValidationUtilsTest {

    @Test
    void validateTriggers_whenNoneProvided_throwsInvalidAlertException() {
        // Arrange
        BigDecimal desiredPrice = null;
        BigDecimal desiredPercent = null;
        Boolean alertIfSale = false;

        // Act
        InvalidAlertException exception = assertThrows(
                InvalidAlertException.class,
                () -> AlertValidationUtils.validateTriggers(desiredPrice, desiredPercent, alertIfSale)
        );

        // Assert
        assertTrue(exception.getMessage().contains("At least one trigger"));
    }

    @Test
    void validateTriggers_whenAnyTriggerProvided_allows() {
        // Arrange
        BigDecimal desiredPrice = BigDecimal.valueOf(500);
        BigDecimal desiredPercent = null;
        Boolean alertIfSale = false;

        // Act
        AlertValidationUtils.validateTriggers(desiredPrice, desiredPercent, alertIfSale);

        // Assert
        // no exception expected
    }

    @Test
    void validatePricingSnapshot_whenPercentSetWithoutOriginal_throwsInvalidAlertException() {
        // Arrange
        BigDecimal desiredPercent = BigDecimal.valueOf(10);
        BigDecimal originalPrice = null;
        BigDecimal currentPrice = BigDecimal.valueOf(90);

        // Act
        InvalidAlertException exception = assertThrows(
                InvalidAlertException.class,
                () -> AlertValidationUtils.validatePricingSnapshot(desiredPercent, originalPrice, currentPrice)
        );

        // Assert
        assertTrue(exception.getMessage().contains("productOriginalPrice"));
    }

    @Test
    void validatePricingSnapshot_whenCurrentPriceNonPositive_throwsInvalidAlertException() {
        // Arrange
        BigDecimal desiredPercent = null;
        BigDecimal originalPrice = null;
        BigDecimal currentPrice = BigDecimal.ZERO;

        // Act
        InvalidAlertException exception = assertThrows(
                InvalidAlertException.class,
                () -> AlertValidationUtils.validatePricingSnapshot(desiredPercent, originalPrice, currentPrice)
        );

        // Assert
        assertTrue(exception.getMessage().contains("productCurrentPrice"));
    }

    @Test
    void validatePricingSnapshot_whenValid_allows() {
        // Arrange
        BigDecimal desiredPercent = BigDecimal.valueOf(15);
        BigDecimal originalPrice = BigDecimal.valueOf(200);
        BigDecimal currentPrice = BigDecimal.valueOf(150);

        // Act
        AlertValidationUtils.validatePricingSnapshot(desiredPercent, originalPrice, currentPrice);

        // Assert
        // no exception expected
    }

    @Test
    void normalizeChannels_whenNull_returnsDefault() {
        // Arrange
        List<String> channels = null;

        // Act
        List<String> normalized = AlertValidationUtils.normalizeChannels(channels);

        // Assert
        assertEquals(List.of("APP_WIDGET"), normalized);
    }

    @Test
    void normalizeChannels_whenDuplicatesAndWhitespace_normalizesAndDedupes() {
        // Arrange
        List<String> channels = List.of(" email ", "APP_WIDGET", "email", " ");

        // Act
        List<String> normalized = AlertValidationUtils.normalizeChannels(channels);

        // Assert
        assertEquals(List.of("EMAIL", "APP_WIDGET"), normalized);
    }

    @Test
    void normalizeChannels_whenCleanedEmpty_returnsDefault() {
        // Arrange
        List<String> channels = List.of(" ", "\t");

        // Act
        List<String> normalized = AlertValidationUtils.normalizeChannels(channels);

        // Assert
        assertEquals(List.of("APP_WIDGET"), normalized);
    }
}
