package com.innowise.orderservice.service;

import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;

/**
 * Write-side use cases for orders (command half of the CQRS-style split with
 * {@link OrderQueryService}). All operations run in a transaction.
 */
public interface OrderCommandService {

    /**
     * Creates a new order in {@code PENDING} status, resolving each requested item and
     * computing the total price as the sum of {@code item.price * quantity}.
     *
     * @param request the order to create; its items must reference existing {@code Item} ids
     * @return the persisted order
     * @throws com.innowise.orderservice.exception.ItemNotFoundException if any referenced item does not exist
     */
    OrderDto create(CreateOrderRequest request);

    /**
     * Updates the status of an existing order and returns it enriched with user data.
     *
     * @param id      the order id
     * @param request the new status
     * @return the updated order, including user info (or {@code null} user if the User Service is unavailable)
     * @throws com.innowise.orderservice.exception.OrderNotFoundException if the order does not exist or is soft-deleted
     */
    OrderWithUserDto update(Long id, UpdateOrderRequest request);

    /**
     * Soft-deletes the order: the row is kept and flagged {@code deleted = true},
     * after which it is hidden from all queries.
     *
     * @param id the order id
     * @throws com.innowise.orderservice.exception.OrderNotFoundException if the order does not exist or is already soft-deleted
     */
    void delete(Long id);
}
