package com.nimbly.phshoesbackend.alerts.core.repository;

import com.nimbly.phshoesbackend.alerts.core.model.ScrapedProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JdbcTest
class WarehouseScrapeRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WarehouseScrapeRepository repository;

    @SpringBootConfiguration
    @Import(WarehouseScrapeRepository.class)
    static class TestConfig {
    }

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("SET MODE MySQL");
        jdbcTemplate.execute("SET NON_KEYWORDS YEAR,MONTH,DAY");
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS PRODUCTION_MARTS");
        jdbcTemplate.execute("DROP TABLE IF EXISTS PRODUCTION_MARTS.FACT_PRODUCT_SHOES");
        jdbcTemplate.execute("""
            CREATE TABLE PRODUCTION_MARTS.FACT_PRODUCT_SHOES (
                ID VARCHAR(64),
                DWID VARCHAR(64),
                BRAND VARCHAR(128),
                TITLE VARCHAR(256),
                SUBTITLE VARCHAR(256),
                URL VARCHAR(512),
                IMAGE VARCHAR(512),
                PRICE_SALE DECIMAL(18,2),
                PRICE_ORIGINAL DECIMAL(18,2),
                YEAR INT,
                MONTH INT,
                DAY INT
            )
        """);
    }

    @Test
    void findByDate_returnsRowsForDate() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 6, 12);
        insertRow("product-1", "dwid-1", "Brand", "Title", "Sub", "https://example.com/p/1",
                "image", new BigDecimal("99.90"), new BigDecimal("120.00"), date);
        insertRow("product-2", "dwid-2", "Other", "Other", null, "https://example.com/p/2",
                "image-2", new BigDecimal("89.00"), new BigDecimal("110.00"), LocalDate.of(2025, 6, 11));

        // Act
        List<ScrapedProduct> results = repository.findByDate(date);

        // Assert
        assertEquals(1, results.size());
        ScrapedProduct product = results.get(0);
        assertEquals("product-1", product.getProductId());
        assertEquals("dwid-1", product.getDwid());
        assertEquals("Brand", product.getBrand());
        assertEquals("Title", product.getTitle());
        assertEquals("Sub", product.getSubtitle());
        assertEquals("https://example.com/p/1", product.getUrl());
        assertEquals("image", product.getImage());
        assertEquals("image", product.getProductImageUrl());
        assertEquals(0, product.getPriceSale().compareTo(new BigDecimal("99.9")));
        assertEquals(0, product.getPriceOriginal().compareTo(new BigDecimal("120")));
    }

    private void insertRow(String productId,
                           String dwid,
                           String brand,
                           String title,
                           String subtitle,
                           String url,
                           String image,
                           BigDecimal priceSale,
                           BigDecimal priceOriginal,
                           LocalDate date) {
        jdbcTemplate.update("""
                INSERT INTO PRODUCTION_MARTS.FACT_PRODUCT_SHOES
                (ID, DWID, BRAND, TITLE, SUBTITLE, URL, IMAGE, PRICE_SALE, PRICE_ORIGINAL, YEAR, MONTH, DAY)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                productId,
                dwid,
                brand,
                title,
                subtitle,
                url,
                image,
                priceSale,
                priceOriginal,
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth());
    }
}
