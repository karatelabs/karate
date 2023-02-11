package mock.contract;

import com.intuit.karate.JsonUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class ConsumerIntegrationTest {
    
    static ConfigurableApplicationContext context;
    static Consumer consumer;
    
    @BeforeAll
    static void beforeAll() {
        String queueName = "DEMO.INTEGRATION";
        context = PaymentService.start(queueName, false);
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);
        consumer = new Consumer(paymentServiceUrl, queueName); 
    }
    
    @Test
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
            synchronized(this) {
                notify();
            }
        });
        synchronized(this) {
            wait(10000);
        }
    }
    
    @AfterAll
    static void afterAll() {
        PaymentService.stop(context);
        consumer.stopQueueConsumer();
    }
    
}
