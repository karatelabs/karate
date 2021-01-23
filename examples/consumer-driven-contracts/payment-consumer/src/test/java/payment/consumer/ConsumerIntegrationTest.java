package payment.consumer;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import payment.producer.Payment;
import payment.producer.PaymentService;

/**
 *
 * @author pthomas3
 */
class ConsumerIntegrationTest {

    static ConfigurableApplicationContext context;
    static Consumer consumer;

    @BeforeAll
    static void beforeAll() {
        context = PaymentService.start();
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);
        consumer = new Consumer(paymentServiceUrl);
    }

    @Test
    void testPaymentCreate() throws Exception {
        Payment payment = new Payment();
        payment.setAmount(5.67);
        payment.setDescription("test one");
        payment = consumer.create(payment);
        assertTrue(payment.getId() > 0);
        assertEquals(payment.getAmount(), 5.67, 0);
        assertEquals(payment.getDescription(), "test one");
    }

    @AfterAll
    static void afterAll() {
        PaymentService.stop(context);
    }

}
