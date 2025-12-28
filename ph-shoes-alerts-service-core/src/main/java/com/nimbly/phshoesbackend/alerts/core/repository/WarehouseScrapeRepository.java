package com.nimbly.phshoesbackend.alerts.core.repository;

import com.nimbly.phshoesbackend.alerts.core.model.ScrapedProduct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class WarehouseScrapeRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Fetches the rows scraped on the given date. Table + columns match catalog service mappings.
     */
    public List<ScrapedProduct> findByDate(LocalDate date) {
        String sql = """
            SELECT ID, DWID, BRAND, TITLE, SUBTITLE, URL, IMAGE, PRICE_SALE, PRICE_ORIGINAL, IMAGE as PRODUCT_IMAGE_URL
              FROM PRODUCTION_MARTS.FACT_PRODUCT_SHOES
             WHERE YEAR = ? AND MONTH = ? AND DAY = ?
        """;

        long started = System.currentTimeMillis();
        List<ScrapedProduct> rows = jdbcTemplate.query(sql,
                ps -> {
                    ps.setInt(1, date.getYear());
                    ps.setInt(2, date.getMonthValue());
                    ps.setInt(3, date.getDayOfMonth());
                },
                (ResultSet rs, int rowNum) -> ScrapedProduct.builder()
                        .productId(rs.getString("ID"))
                        .dwid(rs.getString("DWID"))
                        .brand(rs.getString("BRAND"))
                        .title(rs.getString("TITLE"))
                        .subtitle(rs.getString("SUBTITLE"))
                        .url(rs.getString("URL"))
                        .image(rs.getString("IMAGE"))
                        .productImageUrl(rs.getString("PRODUCT_IMAGE_URL"))
                        .priceSale(readDecimal(rs, "PRICE_SALE"))
                        .priceOriginal(readDecimal(rs, "PRICE_ORIGINAL"))
                        .build());

        log.info("warehouse.fetch date={} count={} tookMs={}",
                date, rows.size(), System.currentTimeMillis() - started);
        return rows;
    }

    private static BigDecimal readDecimal(ResultSet rs, String col) {
        try {
            var val = rs.getBigDecimal(col);
            return val == null ? null : val.stripTrailingZeros();
        } catch (Exception e) {
            return null;
        }
    }
}
