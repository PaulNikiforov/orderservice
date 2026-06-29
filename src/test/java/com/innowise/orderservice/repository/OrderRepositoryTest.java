package com.innowise.orderservice.repository;

import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.config.JpaAuditingConfig;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Item persistItem(String name, BigDecimal price) {
        Item item = new Item();
        item.setName(name);
        item.setPrice(price);
        return itemRepository.save(item);
    }

    @Test
    void save_persistsOrder_withItems() {
        Item item1 = persistItem("Widget", new BigDecimal("9.99"));
        Item item2 = persistItem("Gadget", new BigDecimal("19.99"));

        Order order = new Order();
        order.setUserEmail("user@test.com");
        order.setTotalPrice(new BigDecimal("29.99"));

        OrderItem orderItem1 = new OrderItem();
        orderItem1.setOrder(order);
        orderItem1.setItem(item1);
        orderItem1.setQuantity(1);

        OrderItem orderItem2 = new OrderItem();
        orderItem2.setOrder(order);
        orderItem2.setItem(item2);
        orderItem2.setQuantity(2);

        order.getItems().add(orderItem1);
        order.getItems().add(orderItem2);

        Order saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Order found = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getItems()).hasSize(2);
        assertThat(found.getTotalPrice()).isEqualByComparingTo(new BigDecimal("29.99"));
        assertThat(found.getUserEmail()).isEqualTo("user@test.com");
    }

    @Test
    void save_setsDefaultStatus_PENDING() {
        Order order = new Order();
        order.setUserEmail("user@test.com");
        order.setTotalPrice(BigDecimal.ZERO);

        Order saved = orderRepository.save(order);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void softDelete_orderNotReturnedAfterDeleted() {
        Item item = persistItem("Widget", new BigDecimal("9.99"));

        Order order = new Order();
        order.setUserEmail("user@test.com");
        order.setTotalPrice(new BigDecimal("9.99"));

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setItem(item);
        orderItem.setQuantity(1);
        order.getItems().add(orderItem);

        Order saved = orderRepository.save(order);
        Long orderId = saved.getId();

        saved.setDeleted(true);
        orderRepository.save(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(orderRepository.findById(orderId)).isEmpty();
    }

    @Test
    void cascadeDelete_removesOrderItems() {
        Item item = persistItem("Gadget", new BigDecimal("15.00"));

        Order order = new Order();
        order.setUserEmail("user@test.com");
        order.setTotalPrice(new BigDecimal("15.00"));

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setItem(item);
        orderItem.setQuantity(1);
        order.getItems().add(orderItem);

        Order saved = orderRepository.save(order);
        Long orderId = saved.getId();

        orderRepository.deleteById(orderId);

        Long count = entityManager.getEntityManager()
                .createQuery("SELECT COUNT(oi) FROM OrderItem oi", Long.class)
                .getSingleResult();
        assertThat(count).isZero();
    }
}
