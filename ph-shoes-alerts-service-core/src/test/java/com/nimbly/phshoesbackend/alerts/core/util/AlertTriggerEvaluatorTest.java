package com.nimbly.phshoesbackend.alerts.core.util;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertProductSnapshot;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;
import com.nimbly.phshoesbackend.alerts.core.util.AlertTriggerEvaluator.TriggerDecision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertTriggerEvaluatorTest {

    @Test
    void evaluate_whenDesiredPriceMet_triggersWithReason() {
        // Arrange
        Alert alert = new Alert();
        alert.setDesiredPrice(BigDecimal.valueOf(120));
        alert.setAlertIfSale(false);
        AlertProductSnapshot snapshot = buildSnapshot(BigDecimal.valueOf(110), BigDecimal.valueOf(150));

        // Act
        TriggerDecision decision = AlertTriggerEvaluator.evaluate(alert, snapshot);

        // Assert
        assertTrue(decision.triggered());
        assertEquals("price<=desired", decision.reason());
    }

    @Test
    void evaluate_whenDesiredPercentMet_triggersWithReason() {
        // Arrange
        Alert alert = new Alert();
        alert.setDesiredPercent(BigDecimal.valueOf(20));
        alert.setAlertIfSale(false);
        AlertProductSnapshot snapshot = buildSnapshot(BigDecimal.valueOf(80), BigDecimal.valueOf(100));

        // Act
        TriggerDecision decision = AlertTriggerEvaluator.evaluate(alert, snapshot);

        // Assert
        assertTrue(decision.triggered());
        assertEquals("drop>=20%", decision.reason());
    }

    @Test
    void evaluate_whenOnSaleAndAlertIfSale_triggersWithReason() {
        // Arrange
        Alert alert = new Alert();
        alert.setAlertIfSale(true);
        AlertProductSnapshot snapshot = buildSnapshot(BigDecimal.valueOf(90), BigDecimal.valueOf(120));

        // Act
        TriggerDecision decision = AlertTriggerEvaluator.evaluate(alert, snapshot);

        // Assert
        assertTrue(decision.triggered());
        assertEquals("on-sale", decision.reason());
    }

    @Test
    void evaluate_whenNoTriggersMatched_returnsNotTriggered() {
        // Arrange
        Alert alert = new Alert();
        alert.setDesiredPrice(BigDecimal.valueOf(50));
        alert.setAlertIfSale(false);
        AlertProductSnapshot snapshot = buildSnapshot(BigDecimal.valueOf(90), BigDecimal.valueOf(120));

        // Act
        TriggerDecision decision = AlertTriggerEvaluator.evaluate(alert, snapshot);

        // Assert
        assertFalse(decision.triggered());
        assertNull(decision.reason());
    }

    @Test
    void applyTriggeredAlert_setsSnapshotFields() {
        // Arrange
        Alert alert = new Alert();
        AlertProductSnapshot snapshot = AlertProductSnapshot.builder()
                .productId("product-1")
                .productName("Product 1")
                .productBrand("Brand")
                .productImage("image")
                .productImageUrl("https://example.com/p1.png")
                .productUrl("https://example.com/p1")
                .priceOriginal(BigDecimal.valueOf(150))
                .priceSale(BigDecimal.valueOf(100))
                .build();
        Instant triggeredAt = Instant.parse("2025-01-10T10:00:00Z");

        // Act
        AlertTriggerEvaluator.applyTriggeredAlert(alert, snapshot, triggeredAt);

        // Assert
        assertEquals(AlertStatus.TRIGGERED, alert.getStatus());
        assertEquals(triggeredAt, alert.getLastTriggeredAt());
        assertEquals(triggeredAt, alert.getUpdatedAt());
        assertEquals(0, alert.getProductCurrentPrice().compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, alert.getProductOriginalPrice().compareTo(BigDecimal.valueOf(150)));
        assertEquals("Product 1", alert.getProductName());
        assertEquals("Brand", alert.getProductBrand());
        assertEquals("image", alert.getProductImage());
        assertEquals("https://example.com/p1.png", alert.getProductImageUrl());
        assertEquals("https://example.com/p1", alert.getProductUrl());
        assertNotNull(alert.getProductCurrentPrice());
    }

    private AlertProductSnapshot buildSnapshot(BigDecimal priceSale, BigDecimal priceOriginal) {
        return AlertProductSnapshot.builder()
                .productId("product-1")
                .productName("Product 1")
                .priceSale(priceSale)
                .priceOriginal(priceOriginal)
                .build();
    }
}
