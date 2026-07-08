package com.innowise.orderservice.kafka;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private PaymentEventHandler paymentEventHandler;

    private PaymentEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentEventListener(paymentEventHandler, VALIDATOR);
    }

    @Test
    @DisplayName("listen: delegates a valid event to PaymentEventHandler")
    void listen_delegatesToHandler() {
        PaymentCompletedEvent event = new PaymentCompletedEvent("order-1", PaymentStatus.SUCCESS);

        assertThatCode(() -> listener.listen(event)).doesNotThrowAnyException();

        verify(paymentEventHandler).handle(event);
    }

    @Test
    @DisplayName("listen: an event with a blank orderId fails validation and is never delegated to the handler")
    void listen_whenOrderIdBlank_throwsConstraintViolationExceptionWithoutHandling() {
        PaymentCompletedEvent event = new PaymentCompletedEvent("", PaymentStatus.SUCCESS);

        assertThatThrownBy(() -> listener.listen(event))
                .isInstanceOf(ConstraintViolationException.class);

        verifyNoInteractions(paymentEventHandler);
    }
}
