package com.innowise.orderservice.service;

import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.repository.specification.OrderFilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderQueryService {

    /**
     * Returns a single order enriched with user data.
     *
     * @param id the order id
     * @return the order with user info (user may be {@code null} if the User Service is unavailable)
     * @throws com.innowise.orderservice.exception.OrderNotFoundException if the order does not exist or is soft-deleted
     */
    OrderWithUserDto getById(Long id);

    /**
     * Returns a page of orders matching the given filter, each enriched with user data.
     * User lookups are resolved once per distinct user id to avoid N+1 HTTP calls.
     *
     * @param filter   optional criteria (user id, statuses, created-at range); null/empty fields are ignored
     * @param pageable paging and sorting
     * @return a page of orders with user info
     */
    Page<OrderWithUserDto> getAll(OrderFilterRequest filter, Pageable pageable);
}
