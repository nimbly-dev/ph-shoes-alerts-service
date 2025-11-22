package com.nimbly.phshoesbackend.alerts.core.service.impl;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;
import com.nimbly.phshoesbackend.alerts.core.repository.AlertRepository;
import com.nimbly.phshoesbackend.alerts.core.service.AlertService;
import com.nimbly.phshoesbackend.alerts.core.exception.AlertNotFoundException;
import com.nimbly.phshoesbackend.alerts.core.exception.DuplicateAlertException;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertCreateRequest;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertUpdateRequest;
import com.nimbly.phshoesbackend.alerts.core.util.AlertValidationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRepository repository;

    @Override
    public Alert createAlert(AlertCreateRequest request) {
        AlertValidationUtils.validateTriggers(request.desiredPrice(), request.desiredPercent(), request.alertIfSale());
        AlertValidationUtils.validatePricingSnapshot(request.desiredPercent(), request.productOriginalPrice(), request.productCurrentPrice());

        var now = Instant.now();
        var alert = new Alert();
        alert.setProductId(request.productId());
        alert.setUserId(request.userId());
        alert.setDesiredPrice(request.desiredPrice());
        alert.setDesiredPercent(request.desiredPercent());
        alert.setAlertIfSale(Boolean.TRUE.equals(request.alertIfSale()));
        alert.setChannels(AlertValidationUtils.normalizeChannels(request.channels()));
        alert.setProductName(request.productName());
        alert.setProductBrand(request.productBrand());
        alert.setProductImage(request.productImage());
        alert.setProductImageUrl(request.productImageUrl() != null ? request.productImageUrl() : request.productImage());
        alert.setProductUrl(request.productUrl());
        alert.setProductOriginalPrice(request.productOriginalPrice());
        alert.setProductCurrentPrice(request.productCurrentPrice());
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);

        try {
            repository.create(alert);
        } catch (ConditionalCheckFailedException dup) {
            throw new DuplicateAlertException("Alert already exists for product/user", dup);
        }
        return alert;
    }

    @Override
    public Alert updateAlert(AlertUpdateRequest request) {
        var existing = repository.findByProductAndUser(request.productId(), request.userId())
                .orElseThrow(() -> new AlertNotFoundException("Alert not found"));

        if (request.desiredPrice() != null) existing.setDesiredPrice(request.desiredPrice());
        if (request.desiredPercent() != null) existing.setDesiredPercent(request.desiredPercent());
        if (request.alertIfSale() != null) existing.setAlertIfSale(request.alertIfSale());
        if (request.channels() != null) existing.setChannels(AlertValidationUtils.normalizeChannels(request.channels()));
        if (request.productName() != null) existing.setProductName(request.productName());
        if (request.productBrand() != null) existing.setProductBrand(request.productBrand());
        if (request.productImage() != null) existing.setProductImage(request.productImage());
        if (request.productImageUrl() != null) existing.setProductImageUrl(request.productImageUrl());
        if (request.productUrl() != null) existing.setProductUrl(request.productUrl());
        if (request.productOriginalPrice() != null) existing.setProductOriginalPrice(request.productOriginalPrice());
        if (request.productCurrentPrice() != null) existing.setProductCurrentPrice(request.productCurrentPrice());

        AlertValidationUtils.validateTriggers(existing.getDesiredPrice(), existing.getDesiredPercent(), existing.getAlertIfSale());
        AlertValidationUtils.validatePricingSnapshot(existing.getDesiredPercent(), existing.getProductOriginalPrice(), existing.getProductCurrentPrice());

        if (request.resetStatus()) {
            existing.setStatus(AlertStatus.ACTIVE);
            existing.setLastTriggeredAt(null);
        }
        existing.setUpdatedAt(Instant.now());

        repository.save(existing);
        return existing;
    }

    @Override
    public void deleteAlert(String productId, String userId) {
        repository.delete(productId, userId);
    }

    @Override
    public Optional<Alert> getAlert(String productId, String userId) {
        return repository.findByProductAndUser(productId, userId);
    }

    @Override
    public List<Alert> listAlerts(String userId, int limit) {
        return repository.findByUser(userId, limit);
    }

    @Override
    public List<Alert> searchAlerts(String userId, String query, String brand, int page, int size) {
        int pageSize = size > 0 ? size : 10;
        int fetchLimit = (page >= 0 ? page + 1 : 1) * pageSize;
        var results = repository.findByUserFiltered(userId, query, brand, fetchLimit);
        int from = Math.max(0, page) * pageSize;
        int to = Math.min(results.size(), from + pageSize);
        if (from >= results.size()) return List.of();
        return results.subList(from, to);
    }

    @Override
    public List<Alert> searchAllAlerts(String userId, String query, String brand) {
        return repository.findByUserFiltered(userId, query, brand, 0);
    }
}
