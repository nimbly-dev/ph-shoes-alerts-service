package com.nimbly.phshoesbackend.alerts.core.service;

import com.nimbly.phshoesbackend.alerts.core.model.SchedulerRunSummary;

import java.time.LocalDate;

public interface AlertsSchedulerService {
    SchedulerRunSummary run(LocalDate date);
    SchedulerRunSummary run(LocalDate date, String testEmailNormalized);
}
