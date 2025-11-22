package com.nimbly.phshoesbackend.alerts.core.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record AlertUpdateRequest(
        @NotBlank String productId,
        String userId,

        @Positive(message = "desiredPrice must be > 0")
        BigDecimal desiredPrice,

        @DecimalMax(value = "100.0", message = "desiredPercent must be <= 100")
        BigDecimal desiredPercent,

        Boolean alertIfSale,
        List<String> channels,

        String productName,
        String productBrand,
        String productImage,
        String productImageUrl,
        String productUrl,

        @Positive(message = "productOriginalPrice must be > 0")
        BigDecimal productOriginalPrice,

        @Positive(message = "productCurrentPrice must be > 0")
        BigDecimal productCurrentPrice,

        boolean resetStatus
) { }
