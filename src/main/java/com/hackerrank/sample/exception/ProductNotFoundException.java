package com.hackerrank.sample.exception;

public class ProductNotFoundException extends RuntimeException {

    private final long productId;

    public ProductNotFoundException(long productId) {
        super("product not found: " + productId);
        this.productId = productId;
    }

    public long getProductId() {
        return productId;
    }
}
