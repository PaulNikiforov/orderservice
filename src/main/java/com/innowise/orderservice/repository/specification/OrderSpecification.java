package com.innowise.orderservice.repository.specification;

import com.innowise.orderservice.model.BaseEntity_;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.model.Order_;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

public class OrderSpecification {

    private OrderSpecification() {
    }

    public static Specification<Order> hasUserId(@Nullable Long userId) {
        return (root, query, cb) ->
                userId == null ? cb.conjunction() : cb.equal(root.get(Order_.userId), userId);
    }

    public static Specification<Order> hasStatus(@Nullable OrderStatus status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get(Order_.status), status);
    }

    public static Specification<Order> createdAfter(@Nullable LocalDateTime from) {
        return (root, query, cb) ->
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get(BaseEntity_.createdAt), from);
    }

    public static Specification<Order> createdBefore(@Nullable LocalDateTime to) {
        return (root, query, cb) ->
                to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get(BaseEntity_.createdAt), to);
    }

    public static Specification<Order> fromFilter(OrderFilterRequest filter) {
        return Specification
                .where(hasUserId(filter.userId()))
                .and(hasStatus(filter.status()))
                .and(createdAfter(filter.createdFrom()))
                .and(createdBefore(filter.createdTo()));
    }
}
