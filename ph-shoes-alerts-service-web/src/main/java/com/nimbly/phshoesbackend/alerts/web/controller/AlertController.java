package com.nimbly.phshoesbackend.alerts.web.controller;

import com.nimbly.phshoesbackend.alerts.api.AlertsApi;
import com.nimbly.phshoesbackend.alerts.core.exception.AlertNotFoundException;
import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;
import com.nimbly.phshoesbackend.alerts.core.service.AlertService;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertCreateRequest;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertResponse;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
public class AlertController implements AlertsApi {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public ResponseEntity<AlertResponse> createAlert(AlertCreateRequest alertCreateRequest) {
        Alert created = alertService.createAlert(currentUserId(), alertCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<Void> deleteAlert(String productId) {
        alertService.deleteAlert(productId, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AlertResponse> getAlert(String productId) {
        Alert alert = alertService.getAlert(productId, currentUserId())
                .orElseThrow(() -> new AlertNotFoundException("Alert not found"));
        return ResponseEntity.ok(toResponse(alert));
    }

    @Override
    public ResponseEntity<List<AlertResponse>> listAlerts() {
        List<AlertResponse> responses = alertService.listAlerts(currentUserId(), 0)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<AlertResponse> updateAlert(String productId, AlertUpdateRequest alertUpdateRequest) {
        Alert updated = alertService.updateAlert(productId, currentUserId(), alertUpdateRequest);
        return ResponseEntity.ok(toResponse(updated));
    }

    @GetMapping("/alerts/search")
    public Map<String, Object> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String brand,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size
    ) {
        String userId = currentUserId();
        List<AlertResponse> content = alertService.searchAlerts(userId, q, brand, page, size)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        int totalElements = alertService.searchAllAlerts(userId, q, brand).size();
        int totalPages = (int) Math.ceil((double) totalElements / (size > 0 ? size : 8));
        return Map.of(
                "content", content,
                "page", page,
                "size", size,
                "totalPages", Math.max(totalPages, 1),
                "totalElements", totalElements
        );
    }

    private AlertResponse toResponse(Alert alert) {
        AlertResponse resp = new AlertResponse();
        resp.setProductId(alert.getProductId());
        resp.setUserId(alert.getUserId());
        resp.setProductName(alert.getProductName());
        resp.setProductBrand(alert.getProductBrand());
        resp.setProductImage(alert.getProductImage());
        resp.setProductImageUrl(toUri(alert.getProductImageUrl()));
        resp.setProductUrl(toUri(alert.getProductUrl()));
        if (alert.getProductOriginalPrice() != null) {
            resp.setProductOriginalPrice(alert.getProductOriginalPrice().doubleValue());
        }
        if (alert.getProductCurrentPrice() != null) {
            resp.setProductCurrentPrice(alert.getProductCurrentPrice().doubleValue());
        }
        if (alert.getDesiredPrice() != null) {
            resp.setDesiredPrice(alert.getDesiredPrice().doubleValue());
        }
        if (alert.getDesiredPercent() != null) {
            resp.setDesiredPercent(alert.getDesiredPercent().doubleValue());
        }
        resp.setAlertIfSale(Boolean.TRUE.equals(alert.getAlertIfSale()));       
        if (alert.getChannels() != null) {
            resp.setChannels(alert.getChannels().stream()
                    .map(this::toResponseChannel)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
        if (alert.getStatus() != null) {
            resp.setStatus(toResponseStatus(alert.getStatus()));
        }
        if (alert.getLastTriggeredAt() != null) {
            resp.setLastTriggeredAt(alert.getLastTriggeredAt().atOffset(ZoneOffset.UTC));
        }
        if (alert.getCreatedAt() != null) {
            resp.setCreatedAt(alert.getCreatedAt().atOffset(ZoneOffset.UTC));
        }
        if (alert.getUpdatedAt() != null) {
            resp.setUpdatedAt(alert.getUpdatedAt().atOffset(ZoneOffset.UTC));
        }
        return resp;
    }

    private AlertResponse.ChannelsEnum toResponseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        try {
            return AlertResponse.ChannelsEnum.fromValue(channel);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private AlertResponse.StatusEnum toResponseStatus(AlertStatus status) {
        if (status == null) {
            return null;
        }
        try {
            return AlertResponse.StatusEnum.fromValue(status.name());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private URI toUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Unauthorized");
        }
        return auth.getPrincipal().toString();
    }
}
