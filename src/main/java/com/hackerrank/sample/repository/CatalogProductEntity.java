package com.hackerrank.sample.repository;

import com.hackerrank.sample.model.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Catalog product aggregate (SPEC-002 §2). Invariants INV-1..INV-4 are
 * enforced at construction; the JPA no-arg constructor is package-private
 * and exists only for the persistence provider.
 */
@Entity
@Table(name = "catalog_products")
public class CatalogProductEntity {

    private static final Pattern ATTRIBUTE_KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");
    private static final int MAX_ATTRIBUTES = 20;

    @Id
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column
    private Double rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Category category;

    @Convert(converter = AttributesJsonConverter.class)
    @Column(columnDefinition = "CLOB")
    private Map<String, Object> attributes = new LinkedHashMap<>();

    CatalogProductEntity() {
    }

    public CatalogProductEntity(
            Long id,
            String name,
            String description,
            String imageUrl,
            Double rating,
            Category category,
            Map<String, Object> attributes) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("name length must be 1..200");
        }
        if (description != null && description.length() > 2000) {
            throw new IllegalArgumentException("description length must be <= 2000");
        }
        if (rating != null && (rating < 0.0 || rating > 5.0)) {
            throw new IllegalArgumentException("rating must be within [0.0, 5.0]");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        Map<String, Object> safeAttributes = attributes == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(attributes);
        if (safeAttributes.size() > MAX_ATTRIBUTES) {
            throw new IllegalArgumentException("attributes must not exceed " + MAX_ATTRIBUTES + " keys");
        }
        for (Map.Entry<String, Object> entry : safeAttributes.entrySet()) {
            if (!ATTRIBUTE_KEY.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException("attribute key violates INV-4: " + entry.getKey());
            }
            Object value = entry.getValue();
            if (value != null
                    && !(value instanceof String)
                    && !(value instanceof Number)
                    && !(value instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "attribute value must be String/Number/Boolean (INV-4): " + entry.getKey());
            }
        }
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.rating = rating;
        this.category = category;
        this.attributes = safeAttributes;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Double getRating() {
        return rating;
    }

    public Category getCategory() {
        return category;
    }

    public Map<String, Object> getAttributes() {
        return attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
