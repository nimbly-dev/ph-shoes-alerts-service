package com.nimbly.phshoesbackend.alerts.core.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Lightweight snapshot of catalog pricing info used to evaluate and update alerts.
 */
@Value
@Builder
public class AlertProductSnapshot {
    String productId;
    String productName;
    String productBrand;
    String productImage;
    String productImageUrl;
    String productUrl;
    BigDecimal priceOriginal;
    BigDecimal priceSale;
}
