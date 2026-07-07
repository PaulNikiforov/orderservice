package com.innowise.orderservice.controller;

import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.model.dto.CreateOrderRequest;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UpdateOrderRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String CLAIM_ROLE = "role";
    private static final String ADMIN_ROLE = "ADMIN";

    private final OrderCommandService commandService;
    private final OrderQueryService queryService;

    @PostMapping
    public ResponseEntity<OrderWithUserDto> create(@Valid @RequestBody CreateOrderRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.create(request, callerUserId(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderWithUserDto> getById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(queryService.getById(id, callerUserId(jwt), isAdmin(jwt)));
    }

    @GetMapping
    public ResponseEntity<Page<OrderWithUserDto>> getAll(
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) List<OrderStatus> statuses,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        var filter = new OrderFilterRequest(userEmail, statuses, createdFrom, createdTo);
        return ResponseEntity.ok(queryService.getAll(filter, callerUserId(jwt), isAdmin(jwt), pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderWithUserDto> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateOrderRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(commandService.update(id, request, callerUserId(jwt), isAdmin(jwt)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        commandService.delete(id, callerUserId(jwt), isAdmin(jwt));
        return ResponseEntity.noContent().build();
    }

    private Long callerUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    private boolean isAdmin(Jwt jwt) {
        return ADMIN_ROLE.equals(jwt.getClaimAsString(CLAIM_ROLE));
    }
}
