package com.innowise.orderservice.controller;

import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.specification.OrderFilterRequest;
import com.innowise.orderservice.service.OrderCommandService;
import com.innowise.orderservice.service.OrderQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCommandService commandService;
    private final OrderQueryService queryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto create(@Valid @RequestBody CreateOrderRequest request) {
        return commandService.create(request);
    }

    @GetMapping("/{id}")
    public OrderWithUserDto getById(@PathVariable Long id) {
        return queryService.getById(id);
    }

    @GetMapping
    public Page<OrderWithUserDto> getAll(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        var filter = new OrderFilterRequest(userId, status, createdFrom, createdTo);
        return queryService.getAll(filter, pageable);
    }

    @PutMapping("/{id}")
    public OrderWithUserDto update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateOrderRequest request) {
        return commandService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        commandService.delete(id);
    }
}
