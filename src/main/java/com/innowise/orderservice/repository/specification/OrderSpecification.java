package com.innowise.orderservice.repository.specification;

import com.innowise.orderservice.model.BaseEntity_;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class OrderSpecification {

    private OrderSpecification() {
    }

    public static Specification<Order> hasUserEmail(@Nullable String userEmail) {
        return (root, query, cb) ->
                userEmail == null ? cb.conjunction() : cb.equal(root.get("userEmail"), userEmail);
    }

    public static Specification<Order> hasStatuses(@Nullable List<OrderStatus> statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()) return cb.conjunction();
            List<OrderStatus> filtered = statuses.stream().filter(Objects::nonNull).toList();
            return filtered.isEmpty() ? cb.conjunction() : root.get("status").in(filtered);
        };
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
        return hasUserEmail(filter.userEmail())
                .and(hasStatuses(filter.statuses()))
                .and(createdAfter(filter.createdFrom()))
                .and(createdBefore(filter.createdTo()));
    }
}
