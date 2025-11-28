package com.nimbly.phshoesbackend.alerts.core.service;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertProductSnapshot;
import com.nimbly.phshoesbackend.alerts.core.util.TemplateRenderer;
import com.nimbly.phshoesbackend.notification.core.model.dto.EmailAddress;
import com.nimbly.phshoesbackend.notification.core.model.dto.EmailRequest;
import com.nimbly.phshoesbackend.notification.core.service.NotificationService;
import com.nimbly.phshoesbackend.services.common.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "alerts.scheduler", name = "enabled", havingValue = "true")
public class AlertDigestService {

    private static final String TEMPLATE_PATH = "email/alert-digest.html";

    private final AccountRepository accountRepository;
    private final SuppressionService suppressionService;
    private final NotificationService notificationService;
    private final UnsubscribeService unsubscribeService;
    private final EmailCrypto emailCrypto;
    private final TemplateRenderer templateRenderer;

    public Optional<TriggeredEmailItem> prepareEmailItem(Alert alert, AlertProductSnapshot snapshot, String reason) {
        if (alert.getChannels() == null || alert.getChannels().stream().noneMatch(ch -> "EMAIL".equalsIgnoreCase(ch))) {
            return Optional.empty();
        }
        return Optional.of(new TriggeredEmailItem(snapshot, reason));
    }

    public EmailDeliveryReport sendDigests(Map<String, List<TriggeredEmailItem>> digests) {
        int sent = 0;
        int suppressed = 0;
        int errors = 0;

        for (var entry : digests.entrySet()) {
            String userId = entry.getKey();
            List<TriggeredEmailItem> items = entry.getValue();
            if (items == null || items.isEmpty()) {
                continue;
            }

            try {
                Optional<Account> optionalAccount = accountRepository.findByUserId(userId);
                if (optionalAccount.isEmpty()) {
                    log.warn("alert.email missing account userId={} alerts={}", userId, items.size());
                    continue;
                }

                Account account = optionalAccount.get();
                String emailPlain = safeDecrypt(account.getEmailEnc());
                if (!StringUtils.hasText(emailPlain)) {
                    log.warn("alert.email decrypt failed userId={} alerts={}", userId, items.size());
                    continue;
                }

                if (suppressionService.shouldBlock(emailPlain)) {
                    log.info("alert.email suppressed userId={} email={}", userId, mask(emailPlain));
                    suppressed++;
                    continue;
                }

                EmailRequest request = buildDigestEmail(emailPlain, account.getEmailHash(), userId, items);
                notificationService.sendEmailVerification(request);
                sent++;

                log.info("alert.email sent digest userId={} email={} alerts={}", userId, mask(emailPlain), items.size());
            } catch (Exception ex) {
                errors++;
                log.warn("alert.email digest failed userId={} msg={}", userId, ex.getMessage());
            }
        }

        return new EmailDeliveryReport(sent, suppressed, errors);
    }

    public Optional<String> resolveUserIdByNormalizedEmail(String normalizedEmail) {
        if (!StringUtils.hasText(normalizedEmail)) {
            return Optional.empty();
        }
        List<String> hashes = emailCrypto.hashCandidates(normalizedEmail);
        if (hashes == null || hashes.isEmpty()) {
            hashes = List.of(emailCrypto.hash(normalizedEmail));
        }
        for (String hash : hashes) {
            Optional<Account> account = accountRepository.findByEmailHash(hash);
            if (account.isPresent()) {
                return Optional.of(account.get().getUserId());
            }
        }
        return Optional.empty();
    }

    private EmailRequest buildDigestEmail(String email, String emailHash, String userId, List<TriggeredEmailItem> items) {
        String subject = "You have " + items.size() + " price alert" + (items.size() > 1 ? "s" : "");
        String textBody = buildDigestText(items);
        String htmlBody = buildDigestHtml(items);
        String listUnsub = unsubscribeService.buildListUnsubscribeHeader(emailHash)
                .orElse("<mailto:unsubscribe@phshoesproject.com>");

        return EmailRequest.builder()
                .to(EmailAddress.builder().address(email).build())
                .subject(subject)
                .textBody(textBody)
                .htmlBody(htmlBody)
                .tag("category", "price-alert")
                .tag("alert_count", String.valueOf(items.size()))
                .requestIdHint("alert-digest:" + userId + ":" + System.currentTimeMillis())
                .header("List-Unsubscribe", listUnsub)
                .header("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")
                .build();
    }

    private String buildDigestText(List<TriggeredEmailItem> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("Your PH Shoes alerts hit:\n\n");
        for (TriggeredEmailItem item : items) {
            AlertProductSnapshot snapshot = item.snapshot();
            builder.append("- ").append(safeTitle(snapshot)).append("\n");
            if (snapshot.getProductBrand() != null) {
                builder.append("  Brand: ").append(snapshot.getProductBrand()).append("\n");
            }
            builder.append("  Price: ").append(formatMoney(firstNonNull(snapshot.getPriceSale(), snapshot.getPriceOriginal())));
            BigDecimal original = snapshot.getPriceOriginal();
            if (original != null) {
                builder.append(" (orig ").append(formatMoney(original)).append(")");
            }
            builder.append("\n");
            builder.append("  Reason: ").append(item.reason() == null ? "Triggered" : item.reason()).append("\n");
            if (snapshot.getProductUrl() != null) {
                builder.append("  Link: ").append(snapshot.getProductUrl()).append("\n");
            }
            builder.append("\n");
        }
        builder.append("You are receiving this because you set alerts on PH Shoes.");
        return builder.toString();
    }

    private String buildDigestHtml(List<TriggeredEmailItem> items) {
        String renderedItems = items.stream()
                .map(item -> {
                    AlertProductSnapshot snapshot = item.snapshot();
                    BigDecimal sale = firstNonNull(snapshot.getPriceSale(), snapshot.getPriceOriginal());
                    BigDecimal original = snapshot.getPriceOriginal();
                    String originalMarkup = original != null
                            ? "<small>" + formatMoney(original) + "</small>"
                            : "";

                    return """
                      <div class="item">
                        <img src="%s" alt="%s" />
                        <div>
                          <div class="title">%s</div>
                          <div class="meta">Brand: %s</div>
                          <div class="price">%s %s</div>
                          <div class="reason">Reason: %s</div>
                          <div class="cta"><a href="%s" target="_blank">Open product</a></div>
                        </div>
                      </div>
                    """.formatted(
                            orBlank(snapshot.getProductImageUrl() != null ? snapshot.getProductImageUrl() : snapshot.getProductImage()),
                            safeTitle(snapshot),
                            safeTitle(snapshot),
                            orBlank(snapshot.getProductBrand()),
                            formatMoney(sale),
                            originalMarkup,
                            item.reason() == null ? "Triggered" : item.reason(),
                            orBlank(snapshot.getProductUrl())
                    );
                })
                .collect(Collectors.joining());

        return templateRenderer.render(TEMPLATE_PATH, Map.of("items", renderedItems));
    }

    private static BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }

    private String safeTitle(AlertProductSnapshot snapshot) {
        if (snapshot.getProductName() != null && !snapshot.getProductName().isBlank()) {
            return snapshot.getProductName();
        }
        return snapshot.getProductId();
    }

    private String safeDecrypt(String encrypted) {
        try {
            return emailCrypto.decrypt(encrypted);
        } catch (Exception ex) {
            return null;
        }
    }

    private String mask(String email) {
        if (email == null || email.isBlank()) {
            return "(blank)";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "N/A";
        }
        return "PHP " + value.setScale(2, RoundingMode.HALF_UP);
    }

    private String orBlank(String value) {
        return value == null ? "" : value;
    }

    public record TriggeredEmailItem(AlertProductSnapshot snapshot, String reason) {}

    public record EmailDeliveryReport(int sent, int suppressed, int errors) {}
}
