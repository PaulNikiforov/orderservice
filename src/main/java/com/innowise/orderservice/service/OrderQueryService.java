package com.innowise.orderservice.service;

import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.repository.specification.OrderFilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderQueryService {

    /**
     * Returns a single order enriched with user data.
     *
     * @param id           the order id
     * @param callerUserId id пользователя из JWT (`sub`); игнорируется, если {@code admin == true}
     * @param admin        true, если вызывающий имеет роль ADMIN (обходит проверку владения)
     * @return the order with user info (user may be {@code null} if the User Service is unavailable)
     * @throws com.innowise.orderservice.exception.OrderNotFoundException if the order does not exist or is soft-deleted
     * @throws com.innowise.orderservice.exception.OrderAccessDeniedException if a non-admin caller requests another user's order
     */
    OrderWithUserDto getById(Long id, Long callerUserId, boolean admin);

    /**
     * Returns a page of orders matching the given filter, each enriched with user data.
     * User lookups are resolved once per distinct user id to avoid N+1 HTTP calls.
     *
     * <p>Non-admin callers are always scoped to their own orders: {@code filter.userEmail()} is
     * ignored and replaced with the caller's own email (resolved from {@code callerUserId}).
     * Admins may filter by any email, or omit it to see orders across all users.
     *
     * @param filter       criteria (statuses, created-at range); {@code userEmail} only honored for admins
     * @param callerUserId id пользователя из JWT (`sub`); игнорируется, если {@code admin == true}
     * @param admin        true, если вызывающий имеет роль ADMIN
     * @param pageable     paging and sorting
     * @return a page of orders with user info
     */
    Page<OrderWithUserDto> getAll(OrderFilterRequest filter, Long callerUserId, boolean admin, Pageable pageable);
}
