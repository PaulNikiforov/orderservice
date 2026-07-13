package com.innowise.orderservice.service;

import com.innowise.orderservice.model.dto.CreateOrderRequest;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UpdateOrderRequest;

public interface OrderCommandService {

    /**
     * Creates a new order in {@code PENDING} status, resolving each requested item and
     * computing the total price as the sum of {@code item.price * quantity}.
     *
     * @param request the order to create; its items must reference existing {@code Item} ids
     * @param callerUserId id пользователя из JWT (`sub`), email резолвится через User Service
     * @return the persisted order with user info (user may be {@code null} if the User Service is unavailable)
     * @throws com.innowise.orderservice.exception.ItemNotFoundException if any referenced item does not exist
     */
    OrderWithUserDto create(CreateOrderRequest request, Long callerUserId);

    /**
     * Updates the status of an existing order and returns it enriched with user data.
     *
     * @param id           the order id
     * @param request      the new status
     * @param callerUserId id пользователя из JWT (`sub`); игнорируется, если {@code admin == true}
     * @param admin        true, если вызывающий имеет роль ADMIN (обходит проверку владения)
     * @return the updated order with user info (user may be {@code null} if the User Service is unavailable)
     * @throws com.innowise.orderservice.exception.OrderNotFoundException if the order does not exist or is soft-deleted
     * @throws com.innowise.orderservice.exception.OrderAccessDeniedException if a non-admin caller updates another user's order
     */
    OrderWithUserDto update(Long id, UpdateOrderRequest request, Long callerUserId, boolean admin);

    /**
     * Soft-deletes the order: the row is kept and flagged {@code deleted = true},
     * after which it is hidden from all queries.
     *
     * @param id           the order id
     * @param callerUserId id пользователя из JWT (`sub`); игнорируется, если {@code admin == true}
     * @param admin        true, если вызывающий имеет роль ADMIN (обходит проверку владения)
     * @throws com.innowise.orderservice.exception.OrderNotFoundException if the order does not exist or is already soft-deleted
     * @throws com.innowise.orderservice.exception.OrderAccessDeniedException if a non-admin caller deletes another user's order
     */
    void delete(Long id, Long callerUserId, boolean admin);
}
