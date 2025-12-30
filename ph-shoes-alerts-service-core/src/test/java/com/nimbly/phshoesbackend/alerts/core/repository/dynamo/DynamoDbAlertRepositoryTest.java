package com.nimbly.phshoesbackend.alerts.core.repository.dynamo;

import com.nimbly.phshoesbackend.alerts.core.model.Alert;
import com.nimbly.phshoesbackend.alerts.core.model.AlertStatus;
import com.nimbly.phshoesbackend.alerts.core.model.dynamo.AlertAttrs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDbAlertRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<Alert> table;

    @Mock
    private DynamoDbIndex<Alert> index;

    @InjectMocks
    private DynamoDbAlertRepository repository;

    @Test
    void findByProductAndUser_whenFound_returnsAlert() {
        // Arrange
        stubTable();
        Alert alert = new Alert();
        alert.setProductId("product-1");
        alert.setUserId("user-1");
        when(table.getItem(any(Key.class))).thenReturn(alert);
        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);

        // Act
        Optional<Alert> result = repository.findByProductAndUser("product-1", "user-1");

        // Assert
        assertTrue(result.isPresent());
        assertSame(alert, result.get());
        verify(table).getItem(keyCaptor.capture());
        Key key = keyCaptor.getValue();
        assertEquals("product-1", key.partitionKeyValue().s());
        assertEquals("user-1", key.sortKeyValue().orElseThrow().s());
    }

    @Test
    void findByProductAndUser_whenMissing_returnsEmpty() {
        // Arrange
        stubTable();
        when(table.getItem(any(Key.class))).thenReturn(null);

        // Act
        Optional<Alert> result = repository.findByProductAndUser("product-2", "user-2");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findByUser_whenIndexMissing_returnsEmptyList() {
        // Arrange
        stubTable();
        when(table.index(AlertAttrs.GSI_USER_ID)).thenReturn(null);

        // Act
        List<Alert> result = repository.findByUser("user-3", 10);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findByUser_whenLimitApplied_stopsAtLimit() {
        // Arrange
        stubTable();
        Alert first = new Alert();
        first.setProductId("product-4");
        Alert second = new Alert();
        second.setProductId("product-5");
        when(table.index(AlertAttrs.GSI_USER_ID)).thenReturn(index);
        when(index.query(any(QueryEnhancedRequest.class)))
                .thenReturn(pageIterable(List.of(first, second)));
        ArgumentCaptor<QueryEnhancedRequest> requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);

        // Act
        List<Alert> result = repository.findByUser("user-4", 1);

        // Assert
        assertEquals(1, result.size());
        assertEquals("product-4", result.get(0).getProductId());
        verify(index).query(requestCaptor.capture());
        assertEquals(1, requestCaptor.getValue().limit());
    }

    @Test
    void findByUserFiltered_whenFiltersProvided_appliesQueryAndBrand() {
        // Arrange
        Alert nike = new Alert();
        nike.setProductId("product-6");
        nike.setProductName("Air Zoom");
        nike.setProductBrand("Nike");
        Alert asics = new Alert();
        asics.setProductId("product-7");
        asics.setProductName("Gel");
        asics.setProductBrand("Asics");
        List<Alert> base = List.of(nike, asics);
        DynamoDbAlertRepository spyRepository = Mockito.spy(new DynamoDbAlertRepository(enhancedClient));
        doReturn(base).when(spyRepository).findByUser("user-5", 0);

        // Act
        List<Alert> result = spyRepository.findByUserFiltered("user-5", "nike", null, 0);

        // Assert
        assertEquals(1, result.size());
        assertEquals("product-6", result.get(0).getProductId());
    }

    @Test
    void findByUserFiltered_whenBrandProvided_limitsResults() {
        // Arrange
        Alert first = new Alert();
        first.setProductId("product-8");
        first.setProductBrand("Nike");
        Alert second = new Alert();
        second.setProductId("product-9");
        second.setProductBrand("Nike");
        List<Alert> base = List.of(first, second);
        DynamoDbAlertRepository spyRepository = Mockito.spy(new DynamoDbAlertRepository(enhancedClient));
        doReturn(base).when(spyRepository).findByUser("user-6", 1);

        // Act
        List<Alert> result = spyRepository.findByUserFiltered("user-6", null, "nike", 1);

        // Assert
        assertEquals(1, result.size());
        assertEquals("product-8", result.get(0).getProductId());
    }

    @Test
    void findActiveByProduct_returnsItemsAndBuildsFilter() {
        // Arrange
        stubTable();
        Alert active = new Alert();
        active.setProductId("product-10");
        when(table.query(any(QueryEnhancedRequest.class)))
                .thenReturn(pageIterable(List.of(active)));
        ArgumentCaptor<QueryEnhancedRequest> requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);

        // Act
        List<Alert> result = repository.findActiveByProduct("product-10");

        // Assert
        assertEquals(1, result.size());
        assertEquals("product-10", result.get(0).getProductId());
        verify(table).query(requestCaptor.capture());
        Expression filter = requestCaptor.getValue().filterExpression();
        assertNotNull(filter);
        assertEquals("#s = :active", filter.expression());
        assertEquals(AlertStatus.ACTIVE.name(), filter.expressionValues().get(":active").s());
        assertEquals(AlertAttrs.STATUS, filter.expressionNames().get("#s"));
    }

    @Test
    void create_buildsConditionalPut() {
        // Arrange
        stubTable();
        Alert alert = new Alert();
        alert.setProductId("product-11");
        alert.setUserId("user-11");
        ArgumentCaptor<PutItemEnhancedRequest<Alert>> requestCaptor = ArgumentCaptor.forClass(PutItemEnhancedRequest.class);

        // Act
        repository.create(alert);

        // Assert
        verify(table).putItem(requestCaptor.capture());
        PutItemEnhancedRequest<Alert> request = requestCaptor.getValue();
        assertSame(alert, request.item());
        Expression condition = request.conditionExpression();
        assertNotNull(condition);
        assertEquals("attribute_not_exists(#pk) AND attribute_not_exists(#sk)", condition.expression());
        assertEquals(Map.of("#pk", AlertAttrs.PK_PRODUCT_ID, "#sk", AlertAttrs.SK_USER_ID), condition.expressionNames());
    }

    @Test
    void save_putsItem() {
        // Arrange
        stubTable();
        Alert alert = new Alert();
        alert.setProductId("product-12");

        // Act
        repository.save(alert);

        // Assert
        verify(table).putItem(alert);
    }

    @Test
    void delete_deletesByKey() {
        // Arrange
        stubTable();
        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);

        // Act
        repository.delete("product-13", "user-13");

        // Assert
        verify(table).deleteItem(keyCaptor.capture());
        Key key = keyCaptor.getValue();
        assertEquals("product-13", key.partitionKeyValue().s());
        assertEquals("user-13", key.sortKeyValue().orElseThrow().s());
    }

    @Test
    void updateStatus_whenMissingAlert_doesNothing() {
        // Arrange
        stubTable();
        when(table.getItem(any(Key.class))).thenReturn(null);

        // Act
        repository.updateStatus("product-14", "user-14", AlertStatus.TRIGGERED, Instant.now());

        // Assert
        verify(table, never()).updateItem(any(UpdateItemEnhancedRequest.class));
    }

    @Test
    void updateStatus_whenFound_updatesStatusAndTimestamp() {
        // Arrange
        stubTable();
        Alert alert = new Alert();
        alert.setProductId("product-15");
        alert.setUserId("user-15");
        when(table.getItem(any(Key.class))).thenReturn(alert);
        ArgumentCaptor<UpdateItemEnhancedRequest<Alert>> requestCaptor = ArgumentCaptor.forClass(UpdateItemEnhancedRequest.class);
        Instant triggeredAt = Instant.parse("2025-01-02T10:00:00Z");

        // Act
        repository.updateStatus("product-15", "user-15", AlertStatus.TRIGGERED, triggeredAt);

        // Assert
        verify(table).updateItem(requestCaptor.capture());
        Alert updated = requestCaptor.getValue().item();
        assertEquals(AlertStatus.TRIGGERED, updated.getStatus());
        assertEquals(triggeredAt, updated.getLastTriggeredAt());
        assertNotNull(updated.getUpdatedAt());
    }

    private PageIterable<Alert> pageIterable(List<Alert> items) {
        Page<Alert> page = Page.create(items);
        SdkIterable<Page<Alert>> sdkIterable = () -> List.of(page).iterator();
        return PageIterable.create(sdkIterable);
    }

    private void stubTable() {
        when(enhancedClient.table(eq(AlertAttrs.TABLE), any(TableSchema.class)))
                .thenReturn(table);
    }
}
