package com.nimbly.phshoesbackend.alerts.core.service.impl;

import com.nimbly.phshoesbackend.alerts.core.exception.AlertNotFoundException;
import com.nimbly.phshoesbackend.alerts.core.exception.DuplicateAlertException;
import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertCreateRequest;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertUpdateRequest;
import com.nimbly.phshoesbackend.alerts.core.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    @Mock
    private AlertRepository repository;

    @InjectMocks
    private AlertServiceImpl service;

    @Test
    void createAlert_whenValidRequest_persistsAndReturnsAlert() {
        // Arrange
        String userId = "user-1";
        AlertCreateRequest request = new AlertCreateRequest("product-1", "Product 1", 120.0)
                .desiredPrice(100.0)
                .alertIfSale(false)
                .channels(List.of(AlertCreateRequest.ChannelsEnum.EMAIL))
                .productBrand("Brand")
                .productImage("image")
                .productImageUrl(URI.create("https://example.com/product-1.png"))
                .productUrl(URI.create("https://example.com/product-1"))
                .productOriginalPrice(150.0)
                .productCurrentPrice(120.0);
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);

        // Act
        Alert created = service.createAlert(userId, request);

        // Assert
        verify(repository).create(alertCaptor.capture());
        Alert savedAlert = alertCaptor.getValue();
        assertSame(created, savedAlert);
        assertEquals("product-1", savedAlert.getProductId());
        assertEquals(userId, savedAlert.getUserId());
        assertEquals(AlertStatus.ACTIVE, savedAlert.getStatus());
        assertEquals(0, savedAlert.getDesiredPrice().compareTo(BigDecimal.valueOf(100.0)));
        assertEquals("https://example.com/product-1.png", savedAlert.getProductImageUrl());
        assertEquals("https://example.com/product-1", savedAlert.getProductUrl());
        assertEquals(List.of("EMAIL"), savedAlert.getChannels());
        assertNotNull(savedAlert.getCreatedAt());
        assertEquals(savedAlert.getCreatedAt(), savedAlert.getUpdatedAt());
    }

    @Test
    void createAlert_whenRepositoryRejectsDuplicate_throwsDuplicateAlertException() {
        // Arrange
        AlertCreateRequest request = new AlertCreateRequest("product-2", "Product 2", 200.0)
                .desiredPrice(180.0)
                .alertIfSale(false);
        doThrow(ConditionalCheckFailedException.builder().message("duplicate").build())
                .when(repository)
                .create(any(Alert.class));

        // Act
        DuplicateAlertException exception = assertThrows(
                DuplicateAlertException.class,
                () -> service.createAlert("user-2", request)
        );

        // Assert
        assertTrue(exception.getMessage().contains("Alert already exists"));
    }

    @Test
    void updateAlert_whenMissing_throwsAlertNotFoundException() {
        // Arrange
        when(repository.findByProductAndUser("product-3", "user-3")).thenReturn(Optional.empty());

        // Act
        AlertNotFoundException exception = assertThrows(
                AlertNotFoundException.class,
                () -> service.updateAlert("product-3", "user-3", new AlertUpdateRequest())
        );

        // Assert
        assertTrue(exception.getMessage().contains("Alert not found"));
    }

    @Test
    void updateAlert_whenResetStatusTrue_clearsLastTriggeredAndSetsActive() {
        // Arrange
        Alert existing = new Alert();
        existing.setProductId("product-4");
        existing.setUserId("user-4");
        existing.setDesiredPrice(BigDecimal.valueOf(100));
        existing.setProductCurrentPrice(BigDecimal.valueOf(110));
        existing.setStatus(AlertStatus.TRIGGERED);
        existing.setLastTriggeredAt(Instant.parse("2025-01-01T10:00:00Z"));
        when(repository.findByProductAndUser("product-4", "user-4")).thenReturn(Optional.of(existing));

        AlertUpdateRequest request = new AlertUpdateRequest()
                .desiredPrice(90.0)
                .resetStatus(true)
                .channels(List.of(AlertUpdateRequest.ChannelsEnum.EMAIL));

        // Act
        Alert updated = service.updateAlert("product-4", "user-4", request);

        // Assert
        verify(repository).save(existing);
        assertSame(existing, updated);
        assertEquals(AlertStatus.ACTIVE, updated.getStatus());
        assertEquals(0, updated.getDesiredPrice().compareTo(BigDecimal.valueOf(90.0)));
        assertEquals(List.of("EMAIL"), updated.getChannels());
        assertNotNull(updated.getUpdatedAt());
        assertNull(updated.getLastTriggeredAt());
    }

    @Test
    void searchAlerts_whenPageBeyondResults_returnsEmptyList() {
        // Arrange
        Alert alert = new Alert();
        alert.setProductId("product-5");
        List<Alert> results = List.of(alert);
        when(repository.findByUserFiltered("user-5", "query", "Brand", 3)).thenReturn(results);

        // Act
        List<Alert> page = service.searchAlerts("user-5", "query", "Brand", 2, 1);

        // Assert
        assertTrue(page.isEmpty());
    }

    @Test
    void searchAlerts_whenSizeNonPositive_defaultsToTen() {
        // Arrange
        List<Alert> results = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            Alert alert = new Alert();
            alert.setProductId("product-" + index);
            results.add(alert);
        }
        when(repository.findByUserFiltered("user-6", null, null, 20)).thenReturn(results);

        // Act
        List<Alert> page = service.searchAlerts("user-6", null, null, 1, 0);

        // Assert
        assertEquals(2, page.size());
        assertEquals("product-10", page.get(0).getProductId());
    }

    @Test
    void deleteAlert_delegatesToRepository() {
        // Arrange
        String productId = "product-7";
        String userId = "user-7";

        // Act
        service.deleteAlert(productId, userId);

        // Assert
        verify(repository).delete(productId, userId);
    }
}
