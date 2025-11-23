package com.nimbly.phshoesbackend.alerts.scheduler.service;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;
import com.nimbly.phshoesbackend.alerts.core.repository.AlertRepository;
import com.nimbly.phshoesbackend.alerts.scheduler.config.SchedulerProperties;
import com.nimbly.phshoesbackend.alerts.scheduler.model.ScrapedProduct;
import com.nimbly.phshoesbackend.alerts.scheduler.repository.WarehouseScrapeRepository;
import com.nimbly.phshoesbackend.notification.core.model.dto.EmailAddress;
import com.nimbly.phshoesbackend.notification.core.model.dto.EmailRequest;
import com.nimbly.phshoesbackend.notification.core.service.NotificationService;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final AccountRepository accountRepository;
    private final SuppressionService suppressionService;
    private final NotificationService notificationService;
    private final SchedulerProperties props;
    private final com.nimbly.phshoesbackend.services.common.core.security.EmailCrypto emailCrypto;
    private final com.nimbly.phshoesbackend.alerts.scheduler.util.TemplateRenderer templates;
    private final com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService unsubscribeService;

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

            for (Alert alert : alerts) {
                summary.incAlertsChecked();
                TriggerDecision decision = shouldTrigger(alert, product);
                if (!decision.isTrigger()) continue;

                summary.incTriggered();
                if (!props.isDryRun()) {
                    markTriggered(alert, product, now);
                }

                List<String> channels = alert.getChannels() == null ? List.of("APP_WIDGET") : alert.getChannels();
                boolean wantsEmail = channels.stream().anyMatch("EMAIL"::equalsIgnoreCase);
                if (!props.isDryRun() && wantsEmail) {
                    emailDigests.computeIfAbsent(alert.getUserId(), k -> new ArrayList<>())
                            .add(new TriggeredEmailItem(alert, product, decision.getReason()));
                }

                log.info("alert.widget flagged userId={} productId={} reason={} emailedPending={} channels={}",
                        alert.getUserId(), alert.getProductId(), decision.getReason(), wantsEmail, channels);
            }
        }

        if (!props.isDryRun() && !emailDigests.isEmpty()) {
            sendDigestEmails(emailDigests, summary);
        }

        log.info("scheduler.summary date={} dwid={} scraped={} deduped={} alertsChecked={} triggered={} emailsSent={} suppressed={} errors={}",
                summary.date, summary.effectiveDwid, summary.scrapedCount, summary.dedupedCount, summary.alertsChecked,
                summary.triggered, summary.emailsSent, summary.suppressed, summary.errors);
        return summary.asImmutable();
    }

    private void sendDigestEmails(Map<String, List<TriggeredEmailItem>> digests, MutableSummary summary) {
        for (var entry : digests.entrySet()) {
            String userId = entry.getKey();
            List<TriggeredEmailItem> items = entry.getValue();
            if (items == null || items.isEmpty()) continue;

            try {
                Optional<Account> accountOpt = accountRepository.findByUserId(userId);
                if (accountOpt.isEmpty()) {
                    log.warn("alert.email missing account userId={} alerts={}", userId, items.size());
                    continue;
                }
                Account account = accountOpt.get();
                String emailPlain = safeDecrypt(account.getEmailEnc());
                if (!StringUtils.hasText(emailPlain)) {
                    log.warn("alert.email decrypt failed userId={} alerts={}", userId, items.size());
                    continue;
                }
                if (suppressionService.shouldBlock(emailPlain)) {
                    log.info("alert.email suppressed userId={} email={}", userId, mask(emailPlain));
                    summary.incSuppressed();
                    continue;
                }

                EmailRequest req = buildDigestEmail(emailPlain, account.getEmailHash(), userId, items);
                notificationService.sendEmailVerification(req);
                summary.incEmailsSent();
                log.info("alert.email sent digest userId={} email={} alerts={}", userId, mask(emailPlain), items.size());
            } catch (Exception e) {
                summary.incErrors();
                log.warn("alert.email digest failed userId={} msg={}", userId, e.toString());
            }
        }
    }

    private TriggerDecision shouldTrigger(Alert alert, ScrapedProduct product) {
        BigDecimal sale = firstNonNull(product.getPriceSale(), product.getPriceOriginal());
        BigDecimal original = firstNonNull(product.getPriceOriginal(), sale);

        boolean onSale = sale != null && original != null && sale.compareTo(original) < 0;

        if (alert.getDesiredPrice() != null && sale != null &&
                sale.compareTo(alert.getDesiredPrice()) <= 0) {
            return new TriggerDecision(true, "price<=desired");
        }

        if (alert.getDesiredPercent() != null && sale != null && original != null && original.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal drop = original.subtract(sale)
                    .divide(original, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (drop.compareTo(alert.getDesiredPercent()) >= 0) {
                return new TriggerDecision(true, "drop>=" + alert.getDesiredPercent() + "%");
            }
        }

        if (Boolean.TRUE.equals(alert.getAlertIfSale()) && onSale) {
            return new TriggerDecision(true, "on-sale");
        }

        return TriggerDecision.NO;
    }

    private void markTriggered(Alert alert, ScrapedProduct product, Instant now) {
        BigDecimal sale = firstNonNull(product.getPriceSale(), product.getPriceOriginal());
        BigDecimal original = firstNonNull(product.getPriceOriginal(), sale);

        alert.setStatus(AlertStatus.TRIGGERED);
        alert.setLastTriggeredAt(now);
        alert.setUpdatedAt(now);
        if (sale != null) alert.setProductCurrentPrice(sale);
        if (original != null) alert.setProductOriginalPrice(original);
        if (product.getTitle() != null) alert.setProductName(product.getTitle());
        if (product.getBrand() != null) alert.setProductBrand(product.getBrand());
        if (product.getImage() != null) alert.setProductImage(product.getImage());
        if (product.getUrl() != null) alert.setProductUrl(product.getUrl());

        alertRepository.save(alert);
    }

    private Map<String, ScrapedProduct> dedupeByProduct(List<ScrapedProduct> scraped) {
        return scraped.stream()
                .filter(s -> StringUtils.hasText(s.getProductId()))
                .collect(Collectors.toMap(
                        ScrapedProduct::getProductId,
                        s -> s,
                        this::preferBetterPrice));
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

    private EmailRequest buildDigestEmail(String email, String emailHash, String userId, List<TriggeredEmailItem> alerts) {
        String subject = "You have " + alerts.size() + " price alert" + (alerts.size() > 1 ? "s" : "");
        String text = buildDigestText(alerts);
        String html = buildDigestHtml(alerts);

        String listUnsub = buildListUnsubscribeHeader(emailHash);

        return EmailRequest.builder()
                .to(EmailAddress.builder().address(email).build())
                .subject(subject)
                .textBody(text)
                .htmlBody(html)
                .tag("category", "price-alert")
                .tag("alert_count", String.valueOf(alerts.size()))
                .requestIdHint("alert-digest:" + userId + ":" + System.currentTimeMillis())
                .header("List-Unsubscribe", listUnsub)
                .header("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")
                .build();
    }

    private String buildDigestText(List<TriggeredEmailItem> alerts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Your PH Shoes alerts hit:\n\n");
        for (TriggeredEmailItem t : alerts) {
            BigDecimal sale = firstNonNull(t.product.getPriceSale(), t.product.getPriceOriginal());
            BigDecimal original = firstNonNull(t.product.getPriceOriginal(), sale);
            sb.append("- ").append(safeTitle(t.product)).append("\n");
            if (t.product.getBrand() != null) sb.append("  Brand: ").append(t.product.getBrand()).append("\n");
            sb.append("  Price: ").append(formatMoney(sale));
            if (original != null) sb.append(" (orig ").append(formatMoney(original)).append(")");
            sb.append("\n");
            sb.append("  Reason: ").append(t.reason == null ? "Triggered" : t.reason).append("\n");
            if (t.product.getUrl() != null) sb.append("  Link: ").append(t.product.getUrl()).append("\n");
            sb.append("\n");
        }
        sb.append("You are receiving this because you set alerts on PH Shoes.");
        return sb.toString();
    }

    private String buildDigestHtml(List<TriggeredEmailItem> alerts) {
        StringBuilder items = new StringBuilder();
        for (TriggeredEmailItem t : alerts) {
            BigDecimal sale = firstNonNull(t.product.getPriceSale(), t.product.getPriceOriginal());
            BigDecimal original = firstNonNull(t.product.getPriceOriginal(), sale);
            items.append(String.format("""
              <div class="item">
                <img src="%s" alt="%s" />
                <div>
                  <div class="title">%s</div>
                  <div class="meta">Brand: %s</div>
                  <div class="price">PHP %s %s</div>
                  <div class="reason">Reason: %s</div>
                  <div class="cta"><a href="%s" target="_blank">Open product</a></div>
                </div>
              </div>
            """,
                    orBlank(t.product.getProductImageUrl() != null ? t.product.getProductImageUrl() : t.product.getImage()),
                    safeTitle(t.product),
                    safeTitle(t.product),
                    orBlank(t.product.getBrand()),
                    formatMoney(sale),
                    original != null ? "<small>PHP %s</small>".formatted(formatMoney(original)) : "",
                    t.reason == null ? "Triggered" : t.reason,
                    orBlank(t.product.getUrl())
            ));
        }

        return templates.render("email/alert-digest.html", Map.of(
                "count", String.valueOf(alerts.size()),
                "items", items.toString()
        ));
    }

    private String buildListUnsubscribeHeader(String emailHash) {
        return unsubscribeService.buildListUnsubscribeHeader(emailHash)
                .orElse("<mailto:unsubscribe@phshoesproject.com>");
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) return "N/A";
        return "PHP " + value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String email) {
        if (email == null || email.isBlank()) return null;
        try {
            return emailCrypto.normalize(email);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeDecrypt(String enc) {
        try {
            return emailCrypto.decrypt(enc);
        } catch (Exception e) {
            return null;
        }
    }

    private String mask(String email) {
        if (email == null || email.isBlank()) return "(blank)";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String orBlank(String value) {
        return value == null ? "" : value;
    }

    private String resolveUserId(String testEmailNormalized) {
        if (testEmailNormalized == null) return null;
        List<String> hashes = emailCrypto.hashCandidates(testEmailNormalized);
        for (String h : hashes) {
            Optional<Account> acc = accountRepository.findByEmailHash(h);
            if (acc.isPresent()) {
                return acc.get().getUserId();
            }
        }
        return null;
    }

    private BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }

    @Value
    private static class TriggerDecision {
        boolean trigger;
        String reason;

        static TriggerDecision NO = new TriggerDecision(false, null);
    }

    @Value
    private static class TriggeredEmailItem {
        Alert alert;
        ScrapedProduct product;
        String reason;
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
        void incEmailsSent() { emailsSent++; }
        void incSuppressed() { suppressed++; }
        void incErrors() { errors++; }

        SchedulerRunSummary asImmutable() {
            return new SchedulerRunSummary(date, scrapedCount, dedupedCount,
                    alertsChecked, triggered, emailsSent, suppressed, errors);
        }
    }
}
