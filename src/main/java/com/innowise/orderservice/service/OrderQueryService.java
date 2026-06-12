package com.innowise.orderservice.service;

import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.repository.specification.OrderFilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderQueryService {

    OrderWithUserDto getById(Long id);

    Page<OrderWithUserDto> getAll(OrderFilterRequest filter, Pageable pageable);
}
