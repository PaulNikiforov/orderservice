package com.innowise.orderservice.exception;

public class ItemNotFoundException extends ResourceNotFoundException {

    public ItemNotFoundException(String message) {
        super(message);
    }
}
