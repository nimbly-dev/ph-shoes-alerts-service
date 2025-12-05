package com.nimbly.phshoesbackend.alerts.core.service.impl;

import com.nimbly.phshoesbackend.alerts.core.exception.AlertNotFoundException;
import com.nimbly.phshoesbackend.alerts.core.exception.DuplicateAlertException;
import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;
import com.nimbly.phshoesbackend.alerts.core.repository.AlertRepository;
import com.nimbly.phshoesbackend.alerts.core.service.AlertService;
import com.nimbly.phshoesbackend.alerts.core.util.AlertValidationUtils;
import com.nimbly.phshoesbackend.alerts.core.model.AlertChannel;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertCreateRequest;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRepository repository;

    @Override
    public Alert createAlert(String userId, AlertCreateRequest request) {
        BigDecimal desiredPrice = toBigDecimal(request.getDesiredPrice());
        BigDecimal desiredPercent = toBigDecimal(request.getDesiredPercent());
        BigDecimal originalPrice = toBigDecimal(request.getProductOriginalPrice());
        BigDecimal currentPrice = toBigDecimal(request.getProductCurrentPrice());

        AlertValidationUtils.validateTriggers(desiredPrice, desiredPercent, request.getAlertIfSale());
        AlertValidationUtils.validatePricingSnapshot(desiredPercent, originalPrice, currentPrice);

        Instant now = Instant.now();
        Alert alert = new Alert();
        alert.setProductId(request.getProductId());
        alert.setUserId(userId);
        alert.setDesiredPrice(desiredPrice);
        alert.setDesiredPercent(desiredPercent);
        alert.setAlertIfSale(Boolean.TRUE.equals(request.getAlertIfSale()));
        alert.setChannels(AlertValidationUtils.normalizeChannels(toChannelStrings(request.getChannels())));
        alert.setProductName(request.getProductName());
        alert.setProductBrand(request.getProductBrand());
        alert.setProductImage(request.getProductImage());
        alert.setProductImageUrl(uriToString(request.getProductImageUrl()));
        alert.setProductUrl(uriToString(request.getProductUrl()));
        alert.setProductOriginalPrice(originalPrice);
        alert.setProductCurrentPrice(currentPrice);
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
    public Alert updateAlert(String productId, String userId, AlertUpdateRequest request) {
        Alert existing = repository.findByProductAndUser(productId, userId)
                .orElseThrow(() -> new AlertNotFoundException("Alert not found"));

        if (request.getDesiredPrice() != null) {
            existing.setDesiredPrice(toBigDecimal(request.getDesiredPrice()));
        }
        if (request.getDesiredPercent() != null) {
            existing.setDesiredPercent(toBigDecimal(request.getDesiredPercent()));
        }
        if (request.getAlertIfSale() != null) {
            existing.setAlertIfSale(request.getAlertIfSale());
        }
        if (request.getChannels() != null) {
            existing.setChannels(AlertValidationUtils.normalizeChannels(toChannelStrings(request.getChannels())));
        }
        if (request.getProductName() != null) {
            existing.setProductName(request.getProductName());
        }
        if (request.getProductBrand() != null) {
            existing.setProductBrand(request.getProductBrand());
        }
        if (request.getProductImage() != null) {
            existing.setProductImage(request.getProductImage());
        }
        if (request.getProductImageUrl() != null) {
            existing.setProductImageUrl(uriToString(request.getProductImageUrl()));
        }
        if (request.getProductUrl() != null) {
            existing.setProductUrl(uriToString(request.getProductUrl()));
        }
        if (request.getProductOriginalPrice() != null) {
            existing.setProductOriginalPrice(toBigDecimal(request.getProductOriginalPrice()));
        }
        if (request.getProductCurrentPrice() != null) {
            existing.setProductCurrentPrice(toBigDecimal(request.getProductCurrentPrice()));
        }

        AlertValidationUtils.validateTriggers(existing.getDesiredPrice(), existing.getDesiredPercent(), existing.getAlertIfSale());
        AlertValidationUtils.validatePricingSnapshot(existing.getDesiredPercent(), existing.getProductOriginalPrice(), existing.getProductCurrentPrice());

        if (Boolean.TRUE.equals(request.getResetStatus())) {
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
        if (from >= results.size()) {
            return List.of();
        }
        return results.subList(from, to);
    }

    @Override
    public List<Alert> searchAllAlerts(String userId, String query, String brand) {
        return repository.findByUserFiltered(userId, query, brand, 0);
    }

    private static BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static List<String> toChannelStrings(List<AlertChannel> channels) {
        if (channels == null) {
            return null;
        }
        return channels.stream()
                .filter(channel -> channel != null && channel.getValue() != null)
                .map(AlertChannel::getValue)
                .toList();
    }

    private static String uriToString(URI uri) {
        return uri == null ? null : uri.toString();
    }
}
