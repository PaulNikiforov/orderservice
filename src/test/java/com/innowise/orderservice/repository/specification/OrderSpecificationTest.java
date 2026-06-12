package com.innowise.orderservice.repository.specification;

import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.config.JpaAuditingConfig;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class OrderSpecificationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Order createOrder(Long userId, BigDecimal totalPrice) {
        Order order = new Order();
        order.setUserId(userId);
        order.setTotalPrice(totalPrice);
        return order;
    }

    private Order persistWithCreatedAt(Long userId, BigDecimal totalPrice, LocalDateTime createdAt) {
        Order order = createOrder(userId, totalPrice);
        entityManager.persist(order);
        entityManager.flush();
        // Bypass @CreatedDate immutability — JPQL bulk update skips persistence context,
        // so clear() is mandatory to avoid stale cached state
        entityManager.getEntityManager()
                .createQuery("UPDATE Order o SET o.createdAt = :ts WHERE o.id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", order.getId())
                .executeUpdate();
        entityManager.clear();
        return order;
    }

    private void saveFlushAndClear(Order... orders) {
        for (Order order : orders) {
            orderRepository.save(order);
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findAll_withUserIdSpec_returnsOnlyUserOrders() {
        saveFlushAndClear(
                createOrder(1L, new BigDecimal("10.00")),
                createOrder(1L, new BigDecimal("20.00")),
                createOrder(2L, new BigDecimal("30.00"))
        );

        var found = orderRepository.findAll(OrderSpecification.hasUserId(1L));

        assertThat(found).hasSize(2);
        assertThat(found).allSatisfy(order ->
                assertThat(order.getUserId()).isEqualTo(1L));
    }

    @Test
    void findAll_withStatusSpec_returnsOnlyMatchingStatus() {
        Order order1 = createOrder(1L, new BigDecimal("10.00"));
        order1.setStatus(OrderStatus.PENDING);
        Order order2 = createOrder(2L, new BigDecimal("20.00"));
        order2.setStatus(OrderStatus.CONFIRMED);
        Order order3 = createOrder(3L, new BigDecimal("30.00"));
        order3.setStatus(OrderStatus.PENDING);
        saveFlushAndClear(order1, order2, order3);

        var found = orderRepository.findAll(OrderSpecification.hasStatus(OrderStatus.PENDING));

        assertThat(found).hasSize(2);
        assertThat(found).allSatisfy(order ->
                assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING));
    }

    @Test
    void findAll_withDateRangeSpec_returnsOrdersInRange() {
        LocalDateTime now = LocalDateTime.now();

        persistWithCreatedAt(1L, new BigDecimal("10.00"), now.minusDays(5));
        persistWithCreatedAt(2L, new BigDecimal("20.00"), now.minusDays(1));
        persistWithCreatedAt(3L, new BigDecimal("30.00"), now.plusDays(1));

        var from = now.minusDays(3);
        var to = now.plusDays(3);

        var spec = Specification.where(OrderSpecification.createdAfter(from))
                .and(OrderSpecification.createdBefore(to));
        var found = orderRepository.findAll(spec);

        assertThat(found).hasSize(2);
        assertThat(found).allSatisfy(order ->
                assertThat(order.getCreatedAt()).isBetween(from, to));
    }

    @Test
    void findAll_withCombinedSpecs_filtersCorrectly() {
        Order order1 = createOrder(1L, new BigDecimal("10.00"));
        order1.setStatus(OrderStatus.PENDING);
        Order order2 = createOrder(1L, new BigDecimal("20.00"));
        order2.setStatus(OrderStatus.CONFIRMED);
        Order order3 = createOrder(2L, new BigDecimal("30.00"));
        order3.setStatus(OrderStatus.PENDING);
        saveFlushAndClear(order1, order2, order3);

        var spec = Specification.where(OrderSpecification.hasUserId(1L))
                .and(OrderSpecification.hasStatus(OrderStatus.PENDING));
        var found = orderRepository.findAll(spec);

        assertThat(found).hasSize(1);
        assertThat(found).extracting(Order::getUserId).containsOnly(1L);
        assertThat(found).extracting(Order::getStatus).containsOnly(OrderStatus.PENDING);
    }

    @Test
    void findAll_withNullSpec_returnsAll() {
        saveFlushAndClear(
                createOrder(1L, new BigDecimal("10.00")),
                createOrder(2L, new BigDecimal("20.00"))
        );

        var spec = Specification.where(OrderSpecification.hasUserId(null))
                .and(OrderSpecification.hasStatus(null));
        var found = orderRepository.findAll(spec);

        assertThat(found).hasSize(2);
    }

    @Test
    void findAll_withPageable_returnsPaginatedResult() {
        for (int i = 0; i < 5; i++) {
            orderRepository.save(createOrder(1L, new BigDecimal("10.00")));
        }
        entityManager.flush();
        entityManager.clear();

        var spec = Specification.where(OrderSpecification.hasUserId(1L));
        Page<Order> page = orderRepository.findAll(spec,
                PageRequest.of(0, 3, Sort.by("id").ascending()));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findAll_excludesSoftDeletedOrders() {
        Order order1 = createOrder(1L, new BigDecimal("10.00"));
        Order order2 = createOrder(1L, new BigDecimal("20.00"));
        order2.setDeleted(true);
        saveFlushAndClear(order1, order2);

        var found = orderRepository.findAll(OrderSpecification.hasUserId(1L));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getDeleted()).isFalse();
    }

    @Test
    void fromFilter_composesAllSpecs() {
        Order order1 = createOrder(1L, new BigDecimal("10.00"));
        order1.setStatus(OrderStatus.PENDING);
        Order order2 = createOrder(1L, new BigDecimal("20.00"));
        order2.setStatus(OrderStatus.CONFIRMED);
        Order order3 = createOrder(2L, new BigDecimal("30.00"));
        order3.setStatus(OrderStatus.PENDING);
        saveFlushAndClear(order1, order2, order3);

        var filter = new OrderFilterRequest(1L, OrderStatus.PENDING, null, null);
        var found = orderRepository.findAll(OrderSpecification.fromFilter(filter));

        assertThat(found).hasSize(1);
        assertThat(found).extracting(Order::getUserId).containsOnly(1L);
        assertThat(found).extracting(Order::getStatus).containsOnly(OrderStatus.PENDING);
    }

    @Test
    void fromFilter_withAllNulls_returnsAll() {
        saveFlushAndClear(
                createOrder(1L, new BigDecimal("10.00")),
                createOrder(2L, new BigDecimal("20.00"))
        );

        var filter = new OrderFilterRequest(null, null, null, null);
        var found = orderRepository.findAll(OrderSpecification.fromFilter(filter));

        assertThat(found).hasSize(2);
    }
}
