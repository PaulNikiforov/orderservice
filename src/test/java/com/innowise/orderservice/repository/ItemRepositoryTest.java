package com.innowise.orderservice.repository;

import com.innowise.orderservice.model.Item;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({com.innowise.orderservice.TestcontainersConfiguration.class,
        com.innowise.orderservice.config.JpaAuditingConfig.class})
@ActiveProfiles("test")
class ItemRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    @Test
    void save_persistsItem() {
        Item item = new Item();
        item.setName("Widget");
        item.setPrice(new BigDecimal("19.99"));

        Item saved = itemRepository.save(item);
        Item found = itemRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("Widget");
        assertThat(found.getPrice()).isEqualByComparingTo(new BigDecimal("19.99"));
    }

    @Test
    void save_setsAuditFields() {
        Item item = new Item();
        item.setName("Gadget");
        item.setPrice(new BigDecimal("49.99"));

        Item saved = itemRepository.save(item);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void existsByName_returnsTrueWhenExists() {
        Item item = new Item();
        item.setName("Doohickey");
        item.setPrice(new BigDecimal("9.99"));
        itemRepository.save(item);

        assertThat(itemRepository.existsByName("Doohickey")).isTrue();
    }

    @Test
    void existsByName_returnsFalseWhenNotExists() {
        assertThat(itemRepository.existsByName("NonExistent")).isFalse();
    }

    @Test
    void findById_returnsEmpty_whenNotExists() {
        assertThat(itemRepository.findById(Long.MAX_VALUE)).isEmpty();
    }
}
