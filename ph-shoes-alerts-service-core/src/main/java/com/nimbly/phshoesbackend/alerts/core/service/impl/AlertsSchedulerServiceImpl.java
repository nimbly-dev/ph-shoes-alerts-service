package com.nimbly.phshoesbackend.alerts.core.service.impl;

import com.nimbly.phshoesbackend.alerts.core.config.props.SchedulerProperties;
import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertProductSnapshot;
import com.nimbly.phshoesbackend.alerts.core.model.EmailDeliveryReport;
import com.nimbly.phshoesbackend.alerts.core.model.MutableSchedulerRunSummary;
import com.nimbly.phshoesbackend.alerts.core.model.SchedulerRunSummary;
import com.nimbly.phshoesbackend.alerts.core.model.ScrapedProduct;
import com.nimbly.phshoesbackend.alerts.core.model.TriggeredEmailItem;
import com.nimbly.phshoesbackend.alerts.core.repository.AlertRepository;
import com.nimbly.phshoesbackend.alerts.core.repository.WarehouseScrapeRepository;
import com.nimbly.phshoesbackend.alerts.core.service.AlertDigestService;
import com.nimbly.phshoesbackend.alerts.core.util.AlertTriggerEvaluator;
import com.nimbly.phshoesbackend.alerts.core.util.AlertTriggerEvaluator.TriggerDecision;
import com.nimbly.phshoesbackend.alerts.core.service.AlertsSchedulerService;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertsSchedulerServiceImpl implements AlertsSchedulerService {

    private final WarehouseScrapeRepository warehouseRepo;
    private final AlertRepository alertRepository;
    private final SchedulerProperties props;
    private final AlertDigestService alertDigestService;
    private final EmailCrypto emailCrypto;

    @Override
    public SchedulerRunSummary run(LocalDate date) {
        String normalizedEmail = null;
        String testEmail = props.getTestEmail();
        if (testEmail != null && !testEmail.isBlank()) {
            try {
                normalizedEmail = emailCrypto.normalize(testEmail);
            } catch (Exception e) {
                normalizedEmail = null;
            }
        }
        return run(date, normalizedEmail);
    }

    @Override
    public SchedulerRunSummary run(LocalDate date, String emailNormalized) {
        List<ScrapedProduct> scraped = warehouseRepo.findByDate(date);
        boolean shouldDryRun = props.isDryRun();

        Map<String, ScrapedProduct> byProduct = dedupeByProduct(scraped);

        String userId = alertDigestService.resolveUserIdByNormalizedEmail(emailNormalized).orElse(null);
        if (emailNormalized != null && userId == null) {
            log.warn("scheduler.email provided but no account found; proceeding without email filter");
        }

        MutableSchedulerRunSummary summary = new MutableSchedulerRunSummary(date, scraped.size(), byProduct.size());
        Instant now = Instant.now();

        Map<String, List<TriggeredEmailItem>> emailDigests = new HashMap<>();

        for (ScrapedProduct product : byProduct.values()) {
            List<Alert> alerts = alertRepository.findActiveByProduct(product.getProductId());
            if (alerts.isEmpty()) continue;

            if (userId != null) {
                alerts = alerts.stream()
                        .filter(a -> userId.equals(a.getUserId()))
                        .toList();
                if (alerts.isEmpty()) continue;
            }

            AlertProductSnapshot snapshot = toSnapshot(product);

            for (Alert alert : alerts) {
                summary.incAlertsChecked();
                TriggerDecision decision = AlertTriggerEvaluator.evaluate(alert, snapshot);
                if (!decision.triggered()) {
                    continue;
                }

                summary.incTriggered();
                if (!shouldDryRun) {
                    AlertTriggerEvaluator.applyTriggeredAlert(alert, snapshot, now);
                    alertRepository.save(alert);
                }

                Optional<TriggeredEmailItem> emailItem = alertDigestService.prepareEmailItem(alert, snapshot, decision.reason());
                boolean wantsEmail = emailItem.isPresent();
                if (!shouldDryRun) {
                    emailItem.ifPresent(item -> emailDigests.computeIfAbsent(alert.getUserId(), k -> new ArrayList<>()).add(item));
                }

                List<String> channels = alert.getChannels() == null ? List.of("APP_WIDGET") : alert.getChannels();
                log.info("alert.widget flagged userId={} productId={} reason={} emailedPending={} channels={}",
                        alert.getUserId(), alert.getProductId(), decision.reason(), wantsEmail, channels);
            }
        }

        if (!shouldDryRun && !emailDigests.isEmpty()) {
            EmailDeliveryReport report = alertDigestService.sendDigests(emailDigests);
            summary.applyEmailReport(report);
        }

        log.info("scheduler.summary date={} scraped={} deduped={} alertsChecked={} triggered={} emailsSent={} suppressed={} errors={}",
                summary.getDate(), summary.getScrapedCount(), summary.getDedupedCount(),
                summary.getAlertsChecked(), summary.getTriggered(), summary.getEmailsSent(), summary.getSuppressed(),
                summary.getErrors());
        return summary.asImmutable();
    }

    private Map<String, ScrapedProduct> dedupeByProduct(List<ScrapedProduct> scraped) {
        return scraped.stream()
                .filter(s -> StringUtils.hasText(s.getProductId()))
                .collect(Collectors.toMap(
                        ScrapedProduct::getProductId,
                        s -> s,
                        this::preferBetterPrice));
    }

    private AlertProductSnapshot toSnapshot(ScrapedProduct product) {
        String productTitle = (product.getTitle() != null && !product.getTitle().isBlank())
                ? product.getTitle()
                : product.getProductId();
        return AlertProductSnapshot.builder()
                .productId(product.getProductId())
                .productName(productTitle)
                .productBrand(product.getBrand())
                .productImage(product.getImage())
                .productImageUrl(product.getProductImageUrl())
                .productUrl(product.getUrl())
                .priceOriginal(product.getPriceOriginal())
                .priceSale(product.getPriceSale())
                .build();
    }

    private ScrapedProduct preferBetterPrice(ScrapedProduct a, ScrapedProduct b) {
        BigDecimal aPrice = a.getPriceSale() != null ? a.getPriceSale() : a.getPriceOriginal();
        BigDecimal bPrice = b.getPriceSale() != null ? b.getPriceSale() : b.getPriceOriginal();
        if (aPrice == null) return b;
        if (bPrice == null) return a;
        int cmp = aPrice.compareTo(bPrice);
        if (cmp < 0) return a;
        if (cmp > 0) return b;
        // tie-breaker: latest dwid wins
        if (a.getDwid() == null) return b;
        if (b.getDwid() == null) return a;
        return a.getDwid().compareTo(b.getDwid()) >= 0 ? a : b;
    }

}
