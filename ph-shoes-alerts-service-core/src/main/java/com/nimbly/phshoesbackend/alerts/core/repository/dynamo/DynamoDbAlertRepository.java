package com.nimbly.phshoesbackend.alerts.core.repository.dynamo;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;
import com.nimbly.phshoesbackend.alerts.core.model.dynamo.AlertAttrs;
import com.nimbly.phshoesbackend.alerts.core.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DynamoDbAlertRepository implements AlertRepository {

    private final DynamoDbEnhancedClient enhanced;

    private DynamoDbTable<Alert> table() {
        return enhanced.table(AlertAttrs.TABLE, TableSchema.fromBean(Alert.class));
    }

    private DynamoDbIndex<Alert> byUserId() {
        return table().index(AlertAttrs.GSI_USER_ID);
    }

    @Override
    public Optional<Alert> findByProductAndUser(String productId, String userId) {
        var item = table().getItem(Key.builder()
                .partitionValue(productId)
                .sortValue(userId)
                .build());
        return Optional.ofNullable(item);
    }

    @Override
    public List<Alert> findByUser(String userId, int limit) {
        var idx = byUserId();
        if (idx == null) return Collections.emptyList();

        var out = new ArrayList<Alert>();
        var req = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .limit(limit > 0 ? limit : 100)
                .build();

        var results = idx.query(req);
        
        for (var page : results) {
            for (Alert a : page.items()) {
                out.add(a);
                if (limit > 0 && out.size() >= limit) {
                    return out;
                }
            }
        }
        return out;
    }

    @Override
    public List<Alert> findActiveByProduct(String productId, int limit) {
        var out = new ArrayList<Alert>();
        var filter = Expression.builder()
                .expression("#s = :active")
                .expressionNames(Collections.singletonMap("#s", AlertAttrs.STATUS))
                .expressionValues(Collections.singletonMap(":active",
                        software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS(AlertStatus.ACTIVE.name())))
                .build();

        var req = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(productId).build()))
                .filterExpression(filter)
                .limit(limit > 0 ? limit : 1000)
                .build();

        var results = table().query(req);

        for (var page : results) {
            for (Alert a : page.items()) {
                out.add(a);
                if (limit > 0 && out.size() >= limit) {
                    return out;
                }
            }
        }
        return out;
    }

    @Override
    public void create(Alert alert) {
        var condition = Expression.builder()
                .expression("attribute_not_exists(#pk) AND attribute_not_exists(#sk)")
                .expressionNames(
                        java.util.Map.of(
                                "#pk", AlertAttrs.PK_PRODUCT_ID,
                                "#sk", AlertAttrs.SK_USER_ID))
                .build();

        var req = PutItemEnhancedRequest.builder(Alert.class)
                .item(alert)
                .conditionExpression(condition)
                .build();

        table().putItem(req);
    }

    @Override
    public void save(Alert alert) {
        table().putItem(alert);
    }

    @Override
    public void delete(String productId, String userId) {
        table().deleteItem(Key.builder()
                .partitionValue(productId)
                .sortValue(userId)
                .build());
    }

    @Override
    public void updateStatus(String productId, String userId, AlertStatus status, Instant lastTriggeredAt) {
        var current = findByProductAndUser(productId, userId).orElse(null);
        if (current == null) return;
        current.setStatus(status);
        current.setLastTriggeredAt(lastTriggeredAt);
        current.setUpdatedAt(Instant.now());
        table().updateItem(UpdateItemEnhancedRequest.builder(Alert.class).item(current).build());
    }
}
