package payment.consumer;

import com.intuit.karate.core.MockServer;
import java.io.File;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import payment.producer.Payment;

/**
 *
 * @author pthomas3
 */
class ConsumerIntegrationAgainstMockTest {

    static MockServer server;
    static Consumer consumer;

    @BeforeAll
    static void beforeAll() {
        File file = new File("../payment-producer/src/test/java/payment/producer/mock/payment-mock.feature");
        server = MockServer.feature(file).http(0).build();
        String paymentServiceUrl = "http://localhost:" + server.getPort();
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
        server.stop();
    }

}
