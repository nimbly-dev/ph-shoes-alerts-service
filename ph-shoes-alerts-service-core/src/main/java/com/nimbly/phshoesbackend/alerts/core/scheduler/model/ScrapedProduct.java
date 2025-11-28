package com.nimbly.phshoesbackend.alerts.core.scheduler.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ScrapedProduct {
    String productId;
    String dwid;
    String brand;
    String title;
    String subtitle;
    String url;
    String image;
    String productImageUrl;
    BigDecimal priceSale;
    BigDecimal priceOriginal;
}
