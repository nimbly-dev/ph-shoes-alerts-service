package com.nimbly.phshoesbackend.alerts.core.model;

import lombok.Value;

import java.time.LocalDate;

@Value
public class SchedulerRunSummary {
    LocalDate date;
    int scrapedCount;
    int dedupedCount;
    int alertsChecked;
    int triggered;
    int emailsSent;
    int suppressed;
    int errors;
}
