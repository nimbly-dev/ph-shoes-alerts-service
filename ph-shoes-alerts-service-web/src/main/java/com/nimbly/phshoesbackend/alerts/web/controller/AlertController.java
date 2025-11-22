package com.nimbly.phshoesbackend.alerts.web.controller;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertCreateRequest;
import com.nimbly.phshoesbackend.alerts.core.model.dto.AlertUpdateRequest;
import com.nimbly.phshoesbackend.alerts.core.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Alert create(@Valid @RequestBody AlertCreateRequest req) {
        String userId = currentUserId();
        var enriched = new AlertCreateRequest(
                req.productId(),
                userId,
                req.desiredPrice(),
                req.desiredPercent(),
                req.alertIfSale(),
                req.channels(),
                req.productName(),
                req.productBrand(),
                req.productImage(),
                req.productImageUrl(),
                req.productUrl(),
                req.productOriginalPrice(),
                req.productCurrentPrice()
        );
        return alertService.createAlert(enriched);
    }

    @GetMapping
    public List<Alert> list() {
        return alertService.listAlerts(currentUserId(), 0);
    }

    @GetMapping("/search")
    public java.util.Map<String, Object> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String brand,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size
    ) {
        var userId = currentUserId();
        var content = alertService.searchAlerts(userId, q, brand, page, size);
        var all = alertService.searchAllAlerts(userId, q, brand);
        int totalElements = all.size();
        int totalPages = (int) Math.ceil((double) totalElements / (size > 0 ? size : 8));
        return java.util.Map.of(
                "content", content,
                "page", page,
                "size", size,
                "totalPages", Math.max(totalPages, 1),
                "totalElements", totalElements
        );
    }

    @GetMapping("/{productId}")
    public Alert get(@PathVariable String productId) {
        return alertService.getAlert(productId, currentUserId())
                .orElseThrow(() -> new com.nimbly.phshoesbackend.alerts.core.exception.AlertNotFoundException("Alert not found"));
    }

    @PutMapping("/{productId}")
    public Alert update(@PathVariable String productId, @Valid @RequestBody AlertUpdateRequest req) {
        String userId = currentUserId();
        var merged = new AlertUpdateRequest(
                productId,
                userId,
                req.desiredPrice(),
                req.desiredPercent(),
                req.alertIfSale(),
                req.channels(),
                req.productName(),
                req.productBrand(),
                req.productImage(),
                req.productImageUrl(),
                req.productUrl(),
                req.productOriginalPrice(),
                req.productCurrentPrice(),
                req.resetStatus()
        );
        return alertService.updateAlert(merged);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String productId) {
        alertService.deleteAlert(productId, currentUserId());
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new com.nimbly.phshoesbackend.alerts.core.exception.InvalidAlertException("Unauthenticated");
        }
        return auth.getPrincipal().toString();
    }
}
