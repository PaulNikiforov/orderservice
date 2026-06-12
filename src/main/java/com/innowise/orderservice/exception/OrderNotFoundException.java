package com.innowise.orderservice.exception;

public class OrderNotFoundException extends ResourceNotFoundException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}
