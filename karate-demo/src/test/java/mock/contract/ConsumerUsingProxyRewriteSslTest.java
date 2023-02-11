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
class ConsumerUsingProxyRewriteSslTest {

    static ConfigurableApplicationContext context;
    static MockServer server;
    static Consumer consumer;

    @BeforeAll
    static void beforeAll() {
        // actual service      
        String queueName = "DEMO.PROXY.REWRITE.SSL";
        context = PaymentService.start(queueName, true);
        String paymentServiceUrl = "https://localhost:" + PaymentService.getPort(context);
        // proxy
        server = MockServer
                .feature("classpath:mock/contract/payment-service-proxy.feature")
                // requests will be forwarded / url re-written to paymentServiceUrl
                .arg("paymentServiceUrl", paymentServiceUrl)
                .http(0).build();
        // consumer
        String proxyUrl = "http://localhost:" + server.getPort();
        consumer = new Consumer(proxyUrl, queueName);
    }

    @Test
    public void testPaymentCreate() throws Exception {
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
