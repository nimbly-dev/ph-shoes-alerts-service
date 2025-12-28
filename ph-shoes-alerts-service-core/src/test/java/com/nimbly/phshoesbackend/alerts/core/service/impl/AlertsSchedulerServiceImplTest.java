package com.nimbly.phshoesbackend.alerts.core.service.impl;

import com.nimbly.phshoesbackend.alerts.core.config.props.SchedulerProperties;
import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertProductSnapshot;
import com.nimbly.phshoesbackend.alerts.core.model.SchedulerRunSummary;
import com.nimbly.phshoesbackend.alerts.core.model.ScrapedProduct;
import com.nimbly.phshoesbackend.alerts.core.repository.AlertRepository;
import com.nimbly.phshoesbackend.alerts.core.repository.WarehouseScrapeRepository;
import com.nimbly.phshoesbackend.alerts.core.service.AlertDigestService;
import com.nimbly.phshoesbackend.alerts.core.model.EmailDeliveryReport;
import com.nimbly.phshoesbackend.alerts.core.model.TriggeredEmailItem;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertsSchedulerServiceImplTest {

    @Mock
    private WarehouseScrapeRepository warehouseRepo;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private SchedulerProperties schedulerProperties;

    @Mock
    private AlertDigestService alertDigestService;

    @Mock
    private EmailCrypto emailCrypto;

    @InjectMocks
    private AlertsSchedulerServiceImpl schedulerService;

    @Test
    void run_whenTestEmailProvidedButNoUser_returnsSummaryWithoutProcessing() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 1, 2);
        ScrapedProduct scrapedProduct = ScrapedProduct.builder()
                .productId("product-1")
                .dwid("dwid-1")
                .brand("Brand")
                .title("Title")
                .url("https://example.com/product-1")
                .image("image")
                .productImageUrl("https://example.com/product-1.png")
                .priceSale(BigDecimal.valueOf(120))
                .priceOriginal(BigDecimal.valueOf(150))
                .build();
        when(warehouseRepo.findByDate(date)).thenReturn(List.of(scrapedProduct));
        when(alertDigestService.resolveUserIdByNormalizedEmail("test@example.com")).thenReturn(Optional.empty());
        when(alertRepository.findActiveByProduct("product-1")).thenReturn(List.of());
        when(schedulerProperties.isDryRun()).thenReturn(false);

        // Act
        SchedulerRunSummary summary = schedulerService.run(date, "test@example.com");

        // Assert
        assertEquals(1, summary.getScrapedCount());
        assertEquals(1, summary.getDedupedCount());
        assertEquals(0, summary.getAlertsChecked());
        assertEquals(0, summary.getTriggered());
        assertEquals(0, summary.getEmailsSent());
        assertEquals(0, summary.getSuppressed());
        assertEquals(0, summary.getErrors());
        verify(alertRepository).findActiveByProduct("product-1");
        verify(alertDigestService, never()).sendDigests(anyMap());
    }

    @Test
    void run_whenTriggeredAndNotDryRun_sendsDigestAndMarksTriggered() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 2, 1);
        ScrapedProduct scrapedProduct = ScrapedProduct.builder()
                .productId("product-2")
                .dwid("dwid-2")
                .brand("Brand")
                .title("Product 2")
                .url("https://example.com/product-2")
                .image("image")
                .productImageUrl("https://example.com/product-2.png")
                .priceSale(BigDecimal.valueOf(90))
                .priceOriginal(BigDecimal.valueOf(100))
                .build();
        Alert alert = new Alert();
        alert.setProductId("product-2");
        alert.setUserId("user-1");
        alert.setChannels(List.of("EMAIL"));
        alert.setDesiredPrice(BigDecimal.valueOf(95));
        when(warehouseRepo.findByDate(date)).thenReturn(List.of(scrapedProduct));
        when(schedulerProperties.isDryRun()).thenReturn(false);
        when(alertRepository.findActiveByProduct("product-2")).thenReturn(List.of(alert));
        when(alertDigestService.prepareEmailItem(eq(alert), any(AlertProductSnapshot.class), eq("price<=desired")))
                .thenAnswer(invocation -> Optional.of(new TriggeredEmailItem(invocation.getArgument(1), "triggered")));
        when(alertDigestService.sendDigests(anyMap())).thenReturn(new EmailDeliveryReport(1, 0, 0));

        // Act
        SchedulerRunSummary summary = schedulerService.run(date, null);

        // Assert
        assertEquals(1, summary.getScrapedCount());
        assertEquals(1, summary.getDedupedCount());
        assertEquals(1, summary.getAlertsChecked());
        assertEquals(1, summary.getTriggered());
        assertEquals(1, summary.getEmailsSent());
        assertEquals(0, summary.getSuppressed());
        assertEquals(0, summary.getErrors());
        verify(alertRepository).save(eq(alert));
        verify(alertDigestService).sendDigests(anyMap());
    }

    @Test
    void run_whenNoAlertsFound_returnsSummaryWithoutProcessing() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 6, 1);
        ScrapedProduct scrapedProduct = ScrapedProduct.builder()
                .productId("product-4")
                .dwid("dwid-4")
                .priceSale(BigDecimal.valueOf(90))
                .priceOriginal(BigDecimal.valueOf(110))
                .build();
        when(warehouseRepo.findByDate(date)).thenReturn(List.of(scrapedProduct));
        when(alertRepository.findActiveByProduct("product-4")).thenReturn(List.of());

        // Act
        SchedulerRunSummary summary = schedulerService.run(date, null);

        // Assert
        assertEquals(1, summary.getScrapedCount());
        assertEquals(1, summary.getDedupedCount());
        assertEquals(0, summary.getAlertsChecked());
        assertEquals(0, summary.getTriggered());
        assertEquals(0, summary.getEmailsSent());
        assertEquals(0, summary.getSuppressed());
        assertEquals(0, summary.getErrors());
        verify(alertDigestService, never()).sendDigests(anyMap());
    }

    @Test
    void run_whenAlertsFoundButNotTriggered_skipsDigest() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 7, 4);
        ScrapedProduct scrapedProduct = ScrapedProduct.builder()
                .productId("product-5")
                .dwid("dwid-5")
                .priceSale(BigDecimal.valueOf(120))
                .priceOriginal(BigDecimal.valueOf(150))
                .build();
        Alert alert = new Alert();
        alert.setProductId("product-5");
        alert.setUserId("user-5");
        when(warehouseRepo.findByDate(date)).thenReturn(List.of(scrapedProduct));
        when(schedulerProperties.isDryRun()).thenReturn(false);
        when(alertRepository.findActiveByProduct("product-5")).thenReturn(List.of(alert));

        // Act
        SchedulerRunSummary summary = schedulerService.run(date, null);

        // Assert
        assertEquals(1, summary.getScrapedCount());
        assertEquals(1, summary.getDedupedCount());
        assertEquals(1, summary.getAlertsChecked());
        assertEquals(0, summary.getTriggered());
        assertEquals(0, summary.getEmailsSent());
        assertEquals(0, summary.getSuppressed());
        assertEquals(0, summary.getErrors());
        verify(alertDigestService, never()).sendDigests(anyMap());
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void run_whenDryRun_skipsSendingAndMarking() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 8, 12);
        ScrapedProduct scrapedProduct = ScrapedProduct.builder()
                .productId("product-6")
                .dwid("dwid-6")
                .priceSale(BigDecimal.valueOf(70))
                .priceOriginal(BigDecimal.valueOf(90))
                .build();
        Alert alert = new Alert();
        alert.setProductId("product-6");
        alert.setUserId("user-6");
        alert.setChannels(List.of("EMAIL"));
        alert.setDesiredPrice(BigDecimal.valueOf(75));
        when(warehouseRepo.findByDate(date)).thenReturn(List.of(scrapedProduct));
        when(schedulerProperties.isDryRun()).thenReturn(true);
        when(alertRepository.findActiveByProduct("product-6")).thenReturn(List.of(alert));
        when(alertDigestService.prepareEmailItem(eq(alert), any(AlertProductSnapshot.class), eq("price<=desired")))
                .thenAnswer(invocation -> Optional.of(new TriggeredEmailItem(invocation.getArgument(1), "triggered")));

        // Act
        SchedulerRunSummary summary = schedulerService.run(date, null);

        // Assert
        assertEquals(1, summary.getScrapedCount());
        assertEquals(1, summary.getDedupedCount());
        assertEquals(1, summary.getAlertsChecked());
        assertEquals(1, summary.getTriggered());
        assertEquals(0, summary.getEmailsSent());
        assertEquals(0, summary.getSuppressed());
        assertEquals(0, summary.getErrors());
        verify(alertRepository, never()).save(any(Alert.class));
        verify(alertDigestService, never()).sendDigests(anyMap());
    }

    @Test
    void run_whenNoRowsFound_returnsEmptySummary() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 3, 15);
        when(warehouseRepo.findByDate(date)).thenReturn(List.of());

        // Act
        SchedulerRunSummary summary = schedulerService.run(date, null);

        // Assert
        assertEquals(0, summary.getScrapedCount());
        assertEquals(0, summary.getDedupedCount());
        assertEquals(0, summary.getAlertsChecked());
        assertEquals(0, summary.getTriggered());
        assertEquals(0, summary.getEmailsSent());
        assertEquals(0, summary.getSuppressed());
        assertEquals(0, summary.getErrors());
        verify(alertDigestService, never()).sendDigests(anyMap());
    }
}
