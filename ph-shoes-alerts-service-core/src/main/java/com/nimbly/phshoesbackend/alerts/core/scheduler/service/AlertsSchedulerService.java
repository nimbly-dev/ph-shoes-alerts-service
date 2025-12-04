package com.nimbly.phshoesbackend.alerts.core.scheduler.service;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertProductSnapshot;
import com.nimbly.phshoesbackend.alerts.core.repository.AlertRepository;
import com.nimbly.phshoesbackend.alerts.core.config.props.SchedulerProperties;
import com.nimbly.phshoesbackend.alerts.core.scheduler.model.ScrapedProduct;
import com.nimbly.phshoesbackend.alerts.core.scheduler.repository.WarehouseScrapeRepository;
import com.nimbly.phshoesbackend.alerts.core.service.AlertDigestService;
import com.nimbly.phshoesbackend.alerts.core.service.AlertDigestService.EmailDeliveryReport;
import com.nimbly.phshoesbackend.alerts.core.service.AlertDigestService.TriggeredEmailItem;
import com.nimbly.phshoesbackend.alerts.core.service.AlertTriggerEvaluator;
import com.nimbly.phshoesbackend.alerts.core.service.AlertTriggerEvaluator.TriggerDecision;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertsSchedulerService {

    private final WarehouseScrapeRepository warehouseRepo;
    private final AlertRepository alertRepository;
    private final SchedulerProperties props;
    private final AlertTriggerEvaluator alertTriggerEvaluator;
    private final AlertDigestService alertDigestService;
    private final com.nimbly.phshoesbackend.commons.core.security.EmailCrypto emailCrypto;

    public SchedulerRunSummary run(LocalDate date) {
        return run(date, normalize(props.getTestEmail()));
    }

    public SchedulerRunSummary run(LocalDate date, String testEmailNormalized) {
        List<ScrapedProduct> scraped = warehouseRepo.findByDate(date);
        String effectiveDwid = null;

        if (scraped.isEmpty() && props.isFallbackToLatestWhenEmpty()) {
            var latest = warehouseRepo.findLatestBatch();
            if (latest.products() != null && !latest.products().isEmpty()) {
                scraped = latest.products();
                effectiveDwid = latest.dwid();
                log.warn("scheduler.fallback using latest batch dwid={} because date {} had 0 rows", latest.dwid(), date);
            } else {
                log.warn("scheduler.fallback latest batch empty; date {} has no rows", date);
            }
        }

        Map<String, ScrapedProduct> byProduct = dedupeByProduct(scraped);

        String testUserId = resolveUserId(testEmailNormalized);
        if (testEmailNormalized != null && testUserId == null) {
            log.warn("scheduler.test-email provided but no account found; skipping send");
            return new SchedulerRunSummary(date, scraped.size(), byProduct.size(), 0, 0, 0, 0, 0);
        }

        var summary = new MutableSummary(date, scraped.size(), byProduct.size());
        summary.effectiveDwid = effectiveDwid;
        Instant now = Instant.now();

        Map<String, List<TriggeredEmailItem>> emailDigests = new HashMap<>();

        for (ScrapedProduct product : byProduct.values()) {
            List<Alert> alerts = alertRepository.findActiveByProduct(product.getProductId(), props.getMaxAlertsPerProduct());
            if (alerts.isEmpty()) continue;

            if (testUserId != null) {
                alerts = alerts.stream()
                        .filter(a -> testUserId.equals(a.getUserId()))
                        .toList();
                if (alerts.isEmpty()) continue;
            }

            AlertProductSnapshot snapshot = toSnapshot(product);

            for (Alert alert : alerts) {
                summary.incAlertsChecked();
                TriggerDecision decision = alertTriggerEvaluator.evaluate(alert, snapshot);
                if (!decision.triggered()) {
                    continue;
                }

                summary.incTriggered();
                if (!props.isDryRun()) {
                    alertTriggerEvaluator.markTriggered(alert, snapshot, now);
                }

                var emailItem = alertDigestService.prepareEmailItem(alert, snapshot, decision.reason());
                boolean wantsEmail = emailItem.isPresent();
                if (!props.isDryRun()) {
                    emailItem.ifPresent(item -> emailDigests.computeIfAbsent(alert.getUserId(), k -> new ArrayList<>()).add(item));
                }

                List<String> channels = alert.getChannels() == null ? List.of("APP_WIDGET") : alert.getChannels();
                log.info("alert.widget flagged userId={} productId={} reason={} emailedPending={} channels={}",
                        alert.getUserId(), alert.getProductId(), decision.reason(), wantsEmail, channels);
            }
        }

        if (!props.isDryRun() && !emailDigests.isEmpty()) {
            EmailDeliveryReport report = alertDigestService.sendDigests(emailDigests);
            summary.applyEmailReport(report);
        }

        log.info("scheduler.summary date={} dwid={} scraped={} deduped={} alertsChecked={} triggered={} emailsSent={} suppressed={} errors={}",
                summary.date, summary.effectiveDwid, summary.scrapedCount, summary.dedupedCount, summary.alertsChecked,
                summary.triggered, summary.emailsSent, summary.suppressed, summary.errors);
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
        return AlertProductSnapshot.builder()
                .productId(product.getProductId())
                .productName(safeTitle(product))
                .productBrand(product.getBrand())
                .productImage(product.getImage())
                .productImageUrl(product.getProductImageUrl())
                .productUrl(product.getUrl())
                .priceOriginal(product.getPriceOriginal())
                .priceSale(product.getPriceSale())
                .build();
    }

    private ScrapedProduct preferBetterPrice(ScrapedProduct a, ScrapedProduct b) {
        BigDecimal aPrice = firstNonNull(a.getPriceSale(), a.getPriceOriginal());
        BigDecimal bPrice = firstNonNull(b.getPriceSale(), b.getPriceOriginal());
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

    private String safeTitle(ScrapedProduct product) {
        if (product.getTitle() != null && !product.getTitle().isBlank()) return product.getTitle();
        return product.getProductId();
    }

    private String normalize(String email) {
        if (email == null || email.isBlank()) return null;
        try {
            return emailCrypto.normalize(email);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUserId(String testEmailNormalized) {
        return alertDigestService.resolveUserIdByNormalizedEmail(testEmailNormalized).orElse(null);
    }

    private BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }

    @Value
    public static class SchedulerRunSummary {
        LocalDate date;
        int scrapedCount;
        int dedupedCount;
        int alertsChecked;
        int triggered;
        int emailsSent;
        int suppressed;
        int errors;
    }

    private static class MutableSummary {
        final LocalDate date;
        final int scrapedCount;
        final int dedupedCount;
        String effectiveDwid;
        int alertsChecked;
        int triggered;
        int emailsSent;
        int suppressed;
        int errors;

        MutableSummary(LocalDate date, int scrapedCount, int dedupedCount) {
            this.date = date;
            this.scrapedCount = scrapedCount;
            this.dedupedCount = dedupedCount;
        }

        void incAlertsChecked() { alertsChecked++; }
        void incTriggered() { triggered++; }
        void applyEmailReport(EmailDeliveryReport report) {
            if (report == null) {
                return;
            }
            emailsSent += report.sent();
            suppressed += report.suppressed();
            errors += report.errors();
        }

        SchedulerRunSummary asImmutable() {
            return new SchedulerRunSummary(date, scrapedCount, dedupedCount,
                    alertsChecked, triggered, emailsSent, suppressed, errors);
        }
    }
}
