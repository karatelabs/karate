package mock.contract;

import com.intuit.karate.JsonUtils;
import com.intuit.karate.core.MockServer;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
public class ConsumerUsingProxyHttpTest {

    private static ConfigurableApplicationContext context;
    private static MockServer server;
    private static Consumer consumer;

    @BeforeClass
    public static void beforeClass() {
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

    @AfterClass
    public static void afterClass() {
        server.stop();
        PaymentService.stop(context);
        consumer.stopQueueConsumer();
    }

}
