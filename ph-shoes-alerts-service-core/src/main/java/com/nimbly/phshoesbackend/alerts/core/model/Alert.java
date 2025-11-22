package com.nimbly.phshoesbackend.alerts.core.model;

import com.nimbly.phshoesbackend.alerts.core.model.dynamo.AlertAttrs;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@DynamoDbBean
public class Alert {

    @Getter(onMethod_ = {
            @DynamoDbPartitionKey,
            @DynamoDbAttribute(AlertAttrs.PK_PRODUCT_ID)
    })
    @Setter
    private String productId;

    @Getter(onMethod_ = {
            @DynamoDbSortKey,
            @DynamoDbAttribute(AlertAttrs.SK_USER_ID),
            @DynamoDbSecondaryPartitionKey(indexNames = AlertAttrs.GSI_USER_ID)
    })
    @Setter
    private String userId;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.DESIRED_PRICE)
    })
    @Setter
    private BigDecimal desiredPrice;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.DESIRED_PERCENT)
    })
    @Setter
    private BigDecimal desiredPercent;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.ALERT_IF_SALE)
    })
    @Setter
    private Boolean alertIfSale;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.CHANNELS)
    })
    @Setter
    private List<String> channels;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.PRODUCT_NAME)
    })
    @Setter
    private String productName;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.PRODUCT_BRAND)
    })
    @Setter
    private String productBrand;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.PRODUCT_IMAGE)
    })
    @Setter
    private String productImage;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.PRODUCT_IMAGE_URL)
    })
    @Setter
    private String productImageUrl;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.PRODUCT_URL)
    })
    @Setter
    private String productUrl;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.PRODUCT_ORIGINAL_PRICE)
    })
    @Setter
    private BigDecimal productOriginalPrice;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.PRODUCT_CURRENT_PRICE)
    })
    @Setter
    private BigDecimal productCurrentPrice;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.STATUS)
    })
    @Setter
    private AlertStatus status;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.LAST_TRIGGERED_AT)
    })
    @Setter
    private Instant lastTriggeredAt;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.CREATED_AT)
    })
    @Setter
    private Instant createdAt;

    @Getter(onMethod_ = {
            @DynamoDbAttribute(AlertAttrs.UPDATED_AT)
    })
    @Setter
    private Instant updatedAt;
}
