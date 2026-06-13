package com.innowise.orderservice.controller;

import com.innowise.orderservice.model.dto.CreateOrderRequest;
import com.innowise.orderservice.model.dto.OrderDto;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UpdateOrderRequest;
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

/**
 * REST API for orders under {@code /api/v1/orders}. A thin HTTP layer that delegates to
 * {@link OrderCommandService} (writes) and {@link OrderQueryService} (reads); it holds no
 * business logic. Not-found and validation errors are translated to HTTP responses by
 * {@link com.innowise.orderservice.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCommandService commandService;
    private final OrderQueryService queryService;

    /**
     * Creates a new order. Returns {@code 201 Created}.
     *
     * @param request the order to create (validated)
     * @return the created order
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto create(@Valid @RequestBody CreateOrderRequest request) {
        return commandService.create(request);
    }

    /**
     * Returns a single order with user info, or {@code 404} if it does not exist.
     *
     * @param id the order id
     * @return the order with user info
     */
    @GetMapping("/{id}")
    public OrderWithUserDto getById(@PathVariable Long id) {
        return queryService.getById(id);
    }

    /**
     * Returns a page of orders filtered by the optional query parameters. Defaults to
     * 20 items per page, sorted by creation time descending.
     *
     * @param userId      optional filter by owner user id
     * @param status      optional filter by order status
     * @param createdFrom optional lower bound (inclusive) of the creation timestamp
     * @param createdTo   optional upper bound (inclusive) of the creation timestamp
     * @param pageable    paging and sorting parameters
     * @return a page of orders with user info
     */
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

    /**
     * Updates an order's status. Returns {@code 404} if the order does not exist.
     *
     * @param id      the order id
     * @param request the new status (validated)
     * @return the updated order with user info
     */
    @PutMapping("/{id}")
    public OrderWithUserDto update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateOrderRequest request) {
        return commandService.update(id, request);
    }

    /**
     * Soft-deletes an order. Returns {@code 204 No Content}.
     *
     * @param id the order id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        commandService.delete(id);
    }
}
