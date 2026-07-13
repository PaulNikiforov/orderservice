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
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class OrderSpecificationTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, Month.JUNE, 1, 12, 0, 0);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Order createOrder(String userEmail, BigDecimal totalPrice) {
        Order order = new Order();
        order.setUserEmail(userEmail);
        order.setTotalPrice(totalPrice);
        return order;
    }

    private Order persistWithCreatedAt(String userEmail, BigDecimal totalPrice, LocalDateTime createdAt) {
        Order order = createOrder(userEmail, totalPrice);
        entityManager.persist(order);
        entityManager.flush();
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
    void findAll_withUserEmailSpec_returnsOnlyUserOrders() {
        saveFlushAndClear(
                createOrder("alice@test.com", new BigDecimal("10.00")),
                createOrder("alice@test.com", new BigDecimal("20.00")),
                createOrder("bob@test.com", new BigDecimal("30.00"))
        );

        var found = orderRepository.findAll(OrderSpecification.hasUserEmail("alice@test.com"));

        assertThat(found)
                .hasSize(2)
                .allSatisfy(order -> assertThat(order.getUserEmail()).isEqualTo("alice@test.com"));
    }

    @Test
    void findAll_withStatusSpec_returnsOnlyMatchingStatus() {
        Order order1 = createOrder("a@test.com", new BigDecimal("10.00"));
        order1.setStatus(OrderStatus.PENDING);
        Order order2 = createOrder("b@test.com", new BigDecimal("20.00"));
        order2.setStatus(OrderStatus.CONFIRMED);
        Order order3 = createOrder("c@test.com", new BigDecimal("30.00"));
        order3.setStatus(OrderStatus.PENDING);
        saveFlushAndClear(order1, order2, order3);

        var found = orderRepository.findAll(OrderSpecification.hasStatuses(List.of(OrderStatus.PENDING)));

        assertThat(found)
                .hasSize(2)
                .allSatisfy(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING));
    }

    @Test
    void findAll_withMultipleStatuses_returnsOrdersMatchingAny() {
        Order order1 = createOrder("a@test.com", new BigDecimal("10.00"));
        order1.setStatus(OrderStatus.PENDING);
        Order order2 = createOrder("b@test.com", new BigDecimal("20.00"));
        order2.setStatus(OrderStatus.CONFIRMED);
        Order order3 = createOrder("c@test.com", new BigDecimal("30.00"));
        order3.setStatus(OrderStatus.CANCELLED);
        saveFlushAndClear(order1, order2, order3);

        var found = orderRepository.findAll(
                OrderSpecification.hasStatuses(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED)));

        assertThat(found).hasSize(2);
        assertThat(found).extracting(Order::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.CONFIRMED);
    }

    @Test
    void findAll_withEmptyStatusList_returnsAll() {
        Order order1 = createOrder("a@test.com", new BigDecimal("10.00"));
        order1.setStatus(OrderStatus.PENDING);
        Order order2 = createOrder("b@test.com", new BigDecimal("20.00"));
        order2.setStatus(OrderStatus.CONFIRMED);
        Order order3 = createOrder("c@test.com", new BigDecimal("30.00"));
        order3.setStatus(OrderStatus.CANCELLED);
        saveFlushAndClear(order1, order2, order3);

        var found = orderRepository.findAll(OrderSpecification.hasStatuses(List.of()));

        assertThat(found).hasSize(3);
    }

    @Test
    void findAll_withStatusesNotPresentInData_returnsEmpty() {
        Order order1 = createOrder("a@test.com", new BigDecimal("10.00"));
        order1.setStatus(OrderStatus.PENDING);
        Order order2 = createOrder("b@test.com", new BigDecimal("20.00"));
        order2.setStatus(OrderStatus.PENDING);
        saveFlushAndClear(order1, order2);

        var found = orderRepository.findAll(
                OrderSpecification.hasStatuses(List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)));

        assertThat(found).isEmpty();
    }

    @Test
    void fromFilter_withMultipleStatuses_combinedWithUserEmail() {
        Order order1 = createOrder("alice@test.com", new BigDecimal("10.00"));
        order1.setStatus(OrderStatus.PENDING);
        Order order2 = createOrder("alice@test.com", new BigDecimal("20.00"));
        order2.setStatus(OrderStatus.CONFIRMED);
        Order order3 = createOrder("bob@test.com", new BigDecimal("30.00"));
        order3.setStatus(OrderStatus.PENDING);
        saveFlushAndClear(order1, order2, order3);

        var filter = new OrderFilterRequest("alice@test.com", List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED), null, null);
        var found = orderRepository.findAll(OrderSpecification.fromFilter(filter));

        assertThat(found).hasSize(2)
                .allSatisfy(o -> assertThat(o.getUserEmail()).isEqualTo("alice@test.com"));
        assertThat(found).extracting(Order::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.CONFIRMED);
    }

    @Test
    void findAll_withDateRangeSpec_returnsOrdersInRange() {
        persistWithCreatedAt("a@test.com", new BigDecimal("10.00"), BASE_TIME.minusDays(5));
        persistWithCreatedAt("b@test.com", new BigDecimal("20.00"), BASE_TIME.minusDays(1));
        persistWithCreatedAt("c@test.com", new BigDecimal("30.00"), BASE_TIME.plusDays(1));

        var from = BASE_TIME.minusDays(3);
        var to = BASE_TIME.plusDays(3);

        var spec = OrderSpecification.createdAfter(from)
                .and(OrderSpecification.createdBefore(to));
        var found = orderRepository.findAll(spec);

        assertThat(found)
                .hasSize(2)
                .allSatisfy(order -> assertThat(order.getCreatedAt()).isBetween(from, to));
    }

    @Test
    void findAll_withCombinedSpecs_filtersCorrectly() {
        Order order1 = createOrder("alice@test.com", new BigDecimal("10.00"));
        order1.setStatus(OrderStatus.PENDING);
        Order order2 = createOrder("alice@test.com", new BigDecimal("20.00"));
        order2.setStatus(OrderStatus.CONFIRMED);
        Order order3 = createOrder("bob@test.com", new BigDecimal("30.00"));
        order3.setStatus(OrderStatus.PENDING);
        saveFlushAndClear(order1, order2, order3);

        var spec = OrderSpecification.hasUserEmail("alice@test.com")
                .and(OrderSpecification.hasStatuses(List.of(OrderStatus.PENDING)));
        var found = orderRepository.findAll(spec);

        assertThat(found).hasSize(1);
        assertThat(found).extracting(Order::getUserEmail).containsOnly("alice@test.com");
        assertThat(found).extracting(Order::getStatus).containsOnly(OrderStatus.PENDING);
    }

    @Test
    void findAll_withNullSpec_returnsAll() {
        saveFlushAndClear(
                createOrder("a@test.com", new BigDecimal("10.00")),
                createOrder("b@test.com", new BigDecimal("20.00"))
        );

        var spec = OrderSpecification.hasUserEmail(null)
                .and(OrderSpecification.hasStatuses(null));
        var found = orderRepository.findAll(spec);

        assertThat(found).hasSize(2);
    }

    @Test
    void findAll_withPageable_returnsPaginatedResult() {
        for (int i = 0; i < 5; i++) {
            orderRepository.save(createOrder("alice@test.com", new BigDecimal("10.00")));
        }
        entityManager.flush();
        entityManager.clear();

        var spec = OrderSpecification.hasUserEmail("alice@test.com");
        Page<Order> page = orderRepository.findAll(spec,
                PageRequest.of(0, 3, Sort.by("id").ascending()));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findAll_excludesSoftDeletedOrders() {
        Order order1 = createOrder("alice@test.com", new BigDecimal("10.00"));
        Order order2 = createOrder("alice@test.com", new BigDecimal("20.00"));
        order2.setDeleted(true);
        saveFlushAndClear(order1, order2);

        var found = orderRepository.findAll(OrderSpecification.hasUserEmail("alice@test.com"));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getDeleted()).isFalse();
    }

    @Test
    void fromFilter_composesAllSpecs() {
        Order order1 = createOrder("alice@test.com", new BigDecimal("10.00"));
        order1.setStatus(OrderStatus.PENDING);
        Order order2 = createOrder("alice@test.com", new BigDecimal("20.00"));
        order2.setStatus(OrderStatus.CONFIRMED);
        Order order3 = createOrder("bob@test.com", new BigDecimal("30.00"));
        order3.setStatus(OrderStatus.PENDING);
        saveFlushAndClear(order1, order2, order3);

        var filter = new OrderFilterRequest("alice@test.com", List.of(OrderStatus.PENDING), null, null);
        var found = orderRepository.findAll(OrderSpecification.fromFilter(filter));

        assertThat(found).hasSize(1);
        assertThat(found).extracting(Order::getUserEmail).containsOnly("alice@test.com");
        assertThat(found).extracting(Order::getStatus).containsOnly(OrderStatus.PENDING);
    }

    @Test
    void fromFilter_withAllNulls_returnsAll() {
        saveFlushAndClear(
                createOrder("a@test.com", new BigDecimal("10.00")),
                createOrder("b@test.com", new BigDecimal("20.00"))
        );

        var filter = new OrderFilterRequest(null, null, null, null);
        var found = orderRepository.findAll(OrderSpecification.fromFilter(filter));

        assertThat(found).hasSize(2);
    }

    @Test
    void findAll_dateRange_isInclusiveOnBoundaries() {
        LocalDateTime boundary = BASE_TIME.withNano(0);
        persistWithCreatedAt("a@test.com", new BigDecimal("10.00"), boundary);

        var spec = OrderSpecification.createdAfter(boundary)
                .and(OrderSpecification.createdBefore(boundary));
        var found = orderRepository.findAll(spec);

        assertThat(found).hasSize(1);
    }

    @Test
    void findAll_pagination_excludesSoftDeletedFromTotalElements() {
        Order o1 = createOrder("alice@test.com", new BigDecimal("10.00"));
        Order o2 = createOrder("alice@test.com", new BigDecimal("20.00"));
        Order deleted = createOrder("alice@test.com", new BigDecimal("30.00"));
        deleted.setDeleted(true);
        saveFlushAndClear(o1, o2, deleted);

        Page<Order> page = orderRepository.findAll(
                OrderSpecification.hasUserEmail("alice@test.com"), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }
}
