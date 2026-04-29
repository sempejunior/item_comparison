package com.hackerrank.sample.repository;

import com.hackerrank.sample.model.Condition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Offer aggregate (SPEC-002 §3). Enforces INV-5..INV-8 at construction.
 */
@Entity
@Table(name = "offers")
public class OfferEntity {

    @Id
    private Long id;

    @Column(name = "catalog_product_id", nullable = false)
    private Long catalogProductId;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(name = "seller_name", nullable = false, length = 120)
    private String sellerName;

    @Column(name = "seller_reputation", nullable = false)
    private Integer sellerReputation;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Condition condition;

    @Column(name = "free_shipping", nullable = false)
    private Boolean freeShipping;

    @Column(nullable = false)
    private Integer stock;

    OfferEntity() {
    }

    public OfferEntity(
            Long id,
            Long catalogProductId,
            String sellerId,
            String sellerName,
            Integer sellerReputation,
            BigDecimal price,
            String currency,
            Condition condition,
            Boolean freeShipping,
            Integer stock) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (catalogProductId == null) {
            throw new IllegalArgumentException("catalogProductId must not be null");
        }
        if (sellerId == null || sellerId.isBlank() || sellerId.length() > 64) {
            throw new IllegalArgumentException("sellerId length must be 1..64");
        }
        if (sellerName == null || sellerName.isBlank() || sellerName.length() > 120) {
            throw new IllegalArgumentException("sellerName length must be 1..120");
        }
        if (sellerReputation == null || sellerReputation < 0 || sellerReputation > 5) {
            throw new IllegalArgumentException("sellerReputation must be within [0, 5]");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("price must be >= 0");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("currency must be a valid ISO 4217 code: " + currency);
        }
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        if (freeShipping == null) {
            throw new IllegalArgumentException("freeShipping must not be null");
        }
        if (stock == null || stock < 0) {
            throw new IllegalArgumentException("stock must be >= 0");
        }
        this.id = id;
        this.catalogProductId = catalogProductId;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.sellerReputation = sellerReputation;
        this.price = price.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency;
        this.condition = condition;
        this.freeShipping = freeShipping;
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }

    public Long getCatalogProductId() {
        return catalogProductId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public Integer getSellerReputation() {
        return sellerReputation;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public Condition getCondition() {
        return condition;
    }

    public Boolean getFreeShipping() {
        return freeShipping;
    }

    public Integer getStock() {
        return stock;
    }
}
