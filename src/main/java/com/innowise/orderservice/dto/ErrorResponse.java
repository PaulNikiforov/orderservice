// FILE: src/main/java/com/innowise/orderservice/dto/ErrorResponse.java
package com.innowise.orderservice.dto;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {}
