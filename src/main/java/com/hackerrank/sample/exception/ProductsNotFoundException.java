package com.hackerrank.sample.exception;

import java.util.List;

public class ProductsNotFoundException extends RuntimeException {

    private final List<Long> missingIds;

    public ProductsNotFoundException(List<Long> missingIds) {
        super("products not found: " + missingIds);
        this.missingIds = List.copyOf(missingIds);
    }

    public List<Long> getMissingIds() {
        return missingIds;
    }
}
