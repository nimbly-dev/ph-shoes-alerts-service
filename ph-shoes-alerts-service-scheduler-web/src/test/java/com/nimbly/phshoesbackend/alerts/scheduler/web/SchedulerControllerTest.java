package com.nimbly.phshoesbackend.alerts.scheduler.web;

import com.nimbly.phshoesbackend.alerts.core.model.SchedulerRunSummary;
import com.nimbly.phshoesbackend.alerts.core.service.AlertsSchedulerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerControllerTest {

    @Mock
    private AlertsSchedulerService schedulerService;

    @InjectMocks
    private SchedulerController schedulerController;

    @Test
    void runAlertsScheduler_whenDateProvided_passesDateAndMapsResponse() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 4, 10);
        SchedulerRunSummary summary = new SchedulerRunSummary(date, 10, 8, 5, 2, 1, 1, 0);
        when(schedulerService.run(date, "test@example.com")).thenReturn(summary);

        // Act
        ResponseEntity<com.nimbly.phshoesbackend.alerts.core.model.dto.AlertsSchedulerRunResponse> response =
                schedulerController.runAlertsScheduler(date, "test@example.com");

        // Assert
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(date, response.getBody().getDate());
        assertEquals(10, response.getBody().getScrapedCount());
        assertEquals(8, response.getBody().getDedupedCount());
        assertEquals(5, response.getBody().getAlertsChecked());
        assertEquals(2, response.getBody().getTriggered());
        assertEquals(1, response.getBody().getSuppressed());
        assertEquals(0, response.getBody().getErrors());
        verify(schedulerService).run(date, "test@example.com");
    }

    @Test
    void runAlertsScheduler_whenDateMissing_usesCurrentDate() {
        // Arrange
        LocalDate before = LocalDate.now();
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        when(schedulerService.run(dateCaptor.capture(), eq("test@example.com")))
                .thenReturn(new SchedulerRunSummary(before, 0, 0, 0, 0, 0, 0, 0));

        // Act
        schedulerController.runAlertsScheduler(null, "test@example.com");

        // Assert
        LocalDate after = LocalDate.now();
        LocalDate captured = dateCaptor.getValue();
        assertTrue(captured.equals(before) || captured.equals(after));
    }

    @Test
    void runAlertsScheduler_whenEmailProvided_trimsBeforeCallingService() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 5, 5);
        when(schedulerService.run(date, "trimmed@example.com"))
                .thenReturn(new SchedulerRunSummary(date, 0, 0, 0, 0, 0, 0, 0));

        // Act
        schedulerController.runAlertsScheduler(date, "  trimmed@example.com  ");

        // Assert
        verify(schedulerService).run(date, "trimmed@example.com");
    }

}
