package mock.contract;

import com.intuit.karate.JsonUtils;
import com.intuit.karate.core.MockServer;
import org.springframework.context.ConfigurableApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class ConsumerUsingProxyHttpTest {

    static ConfigurableApplicationContext context;
    static MockServer server;
    static Consumer consumer;

    @BeforeAll
    static void beforeAll() {
        // actual service
        String queueName = "DEMO.PROXY.HTTP";
        context = PaymentService.start(queueName, false);
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);
        // proxy
        server = MockServer
                .feature("classpath:mock/contract/payment-service-proxy.feature")
                // setting 'paymentServiceUrl' to null uses request url as-is (no re-writing) - so acts as an http proxy
                .arg("paymentServiceUrl", null)
                .http(0).build();
        // consumer (using http proxy)
        consumer = new Consumer(paymentServiceUrl, "localhost", server.getPort(), queueName);
    }

    // @Test // TODO armeria upgrade
    void testPaymentCreate() throws Exception {
        Payment payment = new Payment();
        payment.setAmount(5.67);
        payment.setDescription("test one");
        Payment result = consumer.create(payment);
        assertTrue(result.getId() > 0);
        assertEquals(result.getAmount(), 5.67, 0);
        assertEquals(result.getDescription(), "test one");
        consumer.listen(json -> {
            Shipment shipment = JsonUtils.fromJson(json, Shipment.class);
            assertEquals(result.getId(), shipment.getPaymentId());
            assertEquals("shipped", shipment.getStatus());
            synchronized (this) {
                notify();
            }
        });
        synchronized (this) {
            wait(10000);
        }
    }

    @AfterAll
    static void afterAll() {
        server.stop();
        PaymentService.stop(context);
        consumer.stopQueueConsumer();
    }

}
