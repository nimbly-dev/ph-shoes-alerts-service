package com.nimbly.phshoesbackend.alerts.core.service;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertProductSnapshot;
import com.nimbly.phshoesbackend.alerts.core.model.EmailDeliveryReport;
import com.nimbly.phshoesbackend.alerts.core.model.TriggeredEmailItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AlertDigestService {
    Optional<TriggeredEmailItem> prepareEmailItem(Alert alert, AlertProductSnapshot snapshot, String reason);

    EmailDeliveryReport sendDigests(Map<String, List<TriggeredEmailItem>> digests);

    Optional<String> resolveUserIdByNormalizedEmail(String normalizedEmail);
}
