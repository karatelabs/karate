package mock.contract;

import com.intuit.karate.JsonUtils;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
public class ConsumerIntegrationTest {
    
    private static ConfigurableApplicationContext context;
    private static Consumer consumer;
    
    @BeforeClass
    public static void beforeClass() {
        String queueName = "DEMO.INTEGRATION";
        context = PaymentService.start(queueName, false);
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);
        consumer = new Consumer(paymentServiceUrl, queueName); 
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
            synchronized(this) {
                notify();
            }
        });
        synchronized(this) {
            wait(10000);
        }
    }
    
    @AfterClass
    public static void afterClass() {
        PaymentService.stop(context);
        consumer.stopQueueConsumer();
    }
    
}
