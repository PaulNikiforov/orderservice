package com.innowise.orderservice.repository;

import com.innowise.orderservice.model.OrderOutboxEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEvent, Long> {

    Page<OrderOutboxEvent> findByPublishedFalse(Pageable pageable);
}
