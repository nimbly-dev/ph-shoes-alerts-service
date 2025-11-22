package com.nimbly.phshoesbackend.alerts.core.service;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertCreateRequest;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertUpdateRequest;

import java.util.List;
import java.util.Optional;

public interface AlertService {
    Alert createAlert(AlertCreateRequest request);

    Alert updateAlert(AlertUpdateRequest request);

    void deleteAlert(String productId, String userId);

    Optional<Alert> getAlert(String productId, String userId);

    List<Alert> listAlerts(String userId, int limit);
    List<Alert> searchAlerts(String userId, String query, String brand, int page, int size);
    List<Alert> searchAllAlerts(String userId, String query, String brand);
}
