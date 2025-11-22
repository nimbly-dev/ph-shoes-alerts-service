package com.nimbly.phshoesbackend.alerts.core.repository;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository {
    Optional<Alert> findByProductAndUser(String productId, String userId);

    List<Alert> findByUser(String userId, int limit);
    List<Alert> findByUserFiltered(String userId, String query, String brand, int limit);

    List<Alert> findActiveByProduct(String productId, int limit);

    void create(Alert alert);

    void save(Alert alert);

    void delete(String productId, String userId);

    void updateStatus(String productId, String userId, AlertStatus status, Instant lastTriggeredAt);
}
