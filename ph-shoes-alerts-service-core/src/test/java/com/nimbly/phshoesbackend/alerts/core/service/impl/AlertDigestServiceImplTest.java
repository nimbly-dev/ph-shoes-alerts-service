package com.nimbly.phshoesbackend.alerts.core.service.impl;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertProductSnapshot;
import com.nimbly.phshoesbackend.alerts.core.model.EmailDeliveryReport;
import com.nimbly.phshoesbackend.alerts.core.model.TriggeredEmailItem;
import com.nimbly.phshoesbackend.alerts.core.util.TemplateRenderer;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.notification.core.model.dto.EmailRequest;
import com.nimbly.phshoesbackend.notification.core.model.dto.SendResult;
import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import com.nimbly.phshoesbackend.notification.core.service.NotificationService;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertDigestServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SuppressionService suppressionService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationEmailProps notificationEmailProps;

    @Mock
    private UnsubscribeService unsubscribeService;

    @Mock
    private EmailCrypto emailCrypto;

    @Mock
    private TemplateRenderer templateRenderer;

    @InjectMocks
    private AlertDigestServiceImpl service;

    @Test
    void prepareEmailItem_whenChannelsMissing_returnsEmpty() {
        // Arrange
        Alert alert = new Alert();
        alert.setChannels(null);
        AlertProductSnapshot snapshot = buildSnapshot("product-1");

        // Act
        Optional<TriggeredEmailItem> result = service.prepareEmailItem(alert, snapshot, "reason");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void prepareEmailItem_whenEmailChannelPresent_returnsItem() {
        // Arrange
        Alert alert = new Alert();
        alert.setChannels(List.of("app_widget", "email"));
        AlertProductSnapshot snapshot = buildSnapshot("product-2");

        // Act
        Optional<TriggeredEmailItem> result = service.prepareEmailItem(alert, snapshot, "price<=desired");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("price<=desired", result.get().reason());
    }

    @Test
    void resolveUserIdByNormalizedEmail_whenBlank_returnsEmpty() {
        // Arrange
        String normalizedEmail = " ";

        // Act
        Optional<String> userId = service.resolveUserIdByNormalizedEmail(normalizedEmail);

        // Assert
        assertTrue(userId.isEmpty());
        verifyNoInteractions(emailCrypto, accountRepository);
    }

    @Test
    void resolveUserIdByNormalizedEmail_whenCandidatesMissing_fallsBackToHash() {
        // Arrange
        Account account = new Account();
        account.setUserId("user-3");
        when(emailCrypto.hashCandidates("normalized@example.com")).thenReturn(List.of());
        when(emailCrypto.hash("normalized@example.com")).thenReturn("hash-1");
        when(accountRepository.findByEmailHash("hash-1")).thenReturn(Optional.of(account));

        // Act
        Optional<String> userId = service.resolveUserIdByNormalizedEmail("normalized@example.com");

        // Assert
        assertEquals("user-3", userId.orElse(null));
    }

    @Test
    void resolveUserIdByNormalizedEmail_whenCandidateMatches_returnsUserId() {
        // Arrange
        Account account = new Account();
        account.setUserId("user-4");
        when(emailCrypto.hashCandidates("normalized@example.com")).thenReturn(List.of("hash-a", "hash-b"));
        when(accountRepository.findByEmailHash("hash-a")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailHash("hash-b")).thenReturn(Optional.of(account));

        // Act
        Optional<String> userId = service.resolveUserIdByNormalizedEmail("normalized@example.com");

        // Assert
        assertEquals("user-4", userId.orElse(null));
    }

    @Test
    void sendDigests_whenAccountMissing_skipsAndReturnsZeroCounts() {
        // Arrange
        Map<String, List<TriggeredEmailItem>> digests = Map.of(
                "user-5", List.of(buildTriggeredItem("product-5"))
        );
        when(accountRepository.findByUserId("user-5")).thenReturn(Optional.empty());

        // Act
        EmailDeliveryReport report = service.sendDigests(digests);

        // Assert
        assertEquals(0, report.sent());
        assertEquals(0, report.suppressed());
        assertEquals(0, report.errors());
        verifyNoInteractions(notificationService);
    }

    @Test
    void sendDigests_whenSuppressed_countsSuppressed() {
        // Arrange
        Map<String, List<TriggeredEmailItem>> digests = Map.of(
                "user-6", List.of(buildTriggeredItem("product-6"))
        );
        Account account = new Account();
        account.setUserId("user-6");
        account.setEmailEnc("encrypted");
        account.setEmailHash("hash-6");
        when(accountRepository.findByUserId("user-6")).thenReturn(Optional.of(account));
        when(emailCrypto.decrypt("encrypted")).thenReturn("user6@example.com");
        when(suppressionService.shouldBlock("user6@example.com")).thenReturn(true);

        // Act
        EmailDeliveryReport report = service.sendDigests(digests);

        // Assert
        assertEquals(0, report.sent());
        assertEquals(1, report.suppressed());
        assertEquals(0, report.errors());
        verifyNoInteractions(notificationService);
    }

    @Test
    void sendDigests_whenSuccess_sendsEmailAndPopulatesHeaders() {
        // Arrange
        Map<String, List<TriggeredEmailItem>> digests = Map.of(
                "user-7", List.of(buildTriggeredItem("product-7"))
        );
        Account account = new Account();
        account.setUserId("user-7");
        account.setEmailEnc("encrypted");
        account.setEmailHash("hash-7");
        when(accountRepository.findByUserId("user-7")).thenReturn(Optional.of(account));
        when(emailCrypto.decrypt("encrypted")).thenReturn("user7@example.com");
        when(suppressionService.shouldBlock("user7@example.com")).thenReturn(false);
        when(unsubscribeService.buildListUnsubscribeHeader("hash-7"))
                .thenReturn(Optional.of("<mailto:unsubscribe@example.com>"));
        when(notificationEmailProps.getListUnsubscribePost())
                .thenReturn("List-Unsubscribe=One-Click");
        when(templateRenderer.render(eq("email/alert-digest.html"), anyMap()))
                .thenReturn("<html>items</html>");
        when(notificationService.sendEmailVerification(any(EmailRequest.class)))
                .thenReturn(SendResult.builder().messageId("message-1").build());

        // Act
        EmailDeliveryReport report = service.sendDigests(digests);

        // Assert
        assertEquals(1, report.sent());
        assertEquals(0, report.suppressed());
        assertEquals(0, report.errors());
        ArgumentCaptor<EmailRequest> requestCaptor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(notificationService).sendEmailVerification(requestCaptor.capture());
        EmailRequest request = requestCaptor.getValue();
        assertEquals("You have 1 price alert", request.getSubject());
        assertNotNull(request.getHeaders());
        assertEquals("<mailto:unsubscribe@example.com>", request.getHeaders().get("List-Unsubscribe"));
        assertEquals("List-Unsubscribe=One-Click", request.getHeaders().get("List-Unsubscribe-Post"));
        assertEquals("1", request.getTags().get("alert_count"));
    }

    @Test
    void sendDigests_whenNotificationFails_countsError() {
        // Arrange
        Map<String, List<TriggeredEmailItem>> digests = Map.of(
                "user-8", List.of(buildTriggeredItem("product-8"))
        );
        Account account = new Account();
        account.setUserId("user-8");
        account.setEmailEnc("encrypted");
        account.setEmailHash("hash-8");
        when(accountRepository.findByUserId("user-8")).thenReturn(Optional.of(account));
        when(emailCrypto.decrypt("encrypted")).thenReturn("user8@example.com");
        when(suppressionService.shouldBlock("user8@example.com")).thenReturn(false);
        when(unsubscribeService.buildListUnsubscribeHeader("hash-8"))
                .thenReturn(Optional.empty());
        when(templateRenderer.render(eq("email/alert-digest.html"), anyMap()))
                .thenReturn("<html>items</html>");
        doThrow(new IllegalStateException("send failed"))
                .when(notificationService)
                .sendEmailVerification(any(EmailRequest.class));

        // Act
        EmailDeliveryReport report = service.sendDigests(digests);

        // Assert
        assertEquals(0, report.sent());
        assertEquals(0, report.suppressed());
        assertEquals(1, report.errors());
        verify(notificationService).sendEmailVerification(any(EmailRequest.class));
    }

    private AlertProductSnapshot buildSnapshot(String productId) {
        return AlertProductSnapshot.builder()
                .productId(productId)
                .productName("Product " + productId)
                .productBrand("Brand")
                .productImage("image")
                .productImageUrl("https://example.com/" + productId + ".png")
                .productUrl("https://example.com/" + productId)
                .priceOriginal(BigDecimal.valueOf(150))
                .priceSale(BigDecimal.valueOf(100))
                .build();
    }

    private TriggeredEmailItem buildTriggeredItem(String productId) {
        return new TriggeredEmailItem(buildSnapshot(productId), "price<=desired");
    }
}
