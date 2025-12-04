package com.nimbly.phshoesbackend.alerts.core.migrations.steps;

import com.nimbly.phshoesbackend.alerts.core.model.dynamo.AlertAttrs;
import com.nimbly.phshoesbackend.commons.core.migrations.UpgradeContext;
import com.nimbly.phshoesbackend.commons.core.migrations.UpgradeStep;
import com.nimbly.phshoesbackend.commons.core.migrations.utility.TableCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.List;

@Component
@RequiredArgsConstructor
public class Alerts_000_001_CreateTables implements UpgradeStep {

    private static final BillingMode BILLING_MODE = BillingMode.PROVISIONED;
    private static final long DEFAULT_RCU = 1L;
    private static final long DEFAULT_WCU = 1L;

    private final TableCreator tables;

    @Override public String service()     { return "alerts_service"; }
    @Override public String fromVersion() { return "0.0.0"; }
    @Override public String toVersion()   { return "0.0.1"; }
    @Override public String description() { return "Create alerts table"; }

    @Override
    public void apply(UpgradeContext ctx) {
        final ScalarAttributeType S = ScalarAttributeType.S;
        final String table = ctx.tbl(AlertAttrs.TABLE);

        tables.createTableIfNotExists(
                table,
                List.of(
                        AttributeDefinition.builder().attributeName(AlertAttrs.PK_PRODUCT_ID).attributeType(S).build(),
                        AttributeDefinition.builder().attributeName(AlertAttrs.SK_USER_ID).attributeType(S).build()
                ),
                List.of(
                        KeySchemaElement.builder().attributeName(AlertAttrs.PK_PRODUCT_ID).keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName(AlertAttrs.SK_USER_ID).keyType(KeyType.RANGE).build()
                ),
                BILLING_MODE, DEFAULT_RCU, DEFAULT_WCU
        );

        tables.createGsiIfNotExists(
                table,
                AlertAttrs.GSI_USER_ID,
                AlertAttrs.SK_USER_ID,
                S,
                BILLING_MODE,
                DEFAULT_RCU,
                DEFAULT_WCU
        );
    }
}
