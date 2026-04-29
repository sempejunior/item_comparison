package com.hackerrank.sample.exception;

public class InvalidCompareRequestException extends RuntimeException {

    public InvalidCompareRequestException(String message) {
        super(message);
    }
}
