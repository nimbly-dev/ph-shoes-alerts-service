package com.nimbly.phshoesbackend.alerts.core.model.dynamo;

public final class AlertAttrs {
    private AlertAttrs() {}

    public static final String TABLE = "alerts";
    public static final String GSI_USER_ID = "gsi_userId";

    public static final String PK_PRODUCT_ID = "productId";
    public static final String SK_USER_ID = "userId";

    public static final String DESIRED_PRICE = "desiredPrice";
    public static final String DESIRED_PERCENT = "desiredPercent";
    public static final String ALERT_IF_SALE = "alertIfSale";
    public static final String CHANNELS = "channels";
    public static final String PRODUCT_NAME = "productName";
    public static final String PRODUCT_BRAND = "productBrand";
    public static final String PRODUCT_IMAGE = "productImage";
    public static final String PRODUCT_IMAGE_URL = "productImageUrl";
    public static final String PRODUCT_URL = "productUrl";
    public static final String PRODUCT_ORIGINAL_PRICE = "productOriginalPrice";
    public static final String PRODUCT_CURRENT_PRICE = "productCurrentPrice";
    public static final String STATUS = "status";
    public static final String LAST_TRIGGERED_AT = "lastTriggeredAt";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
}
