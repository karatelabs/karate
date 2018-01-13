package mock.contract;

import java.util.List;
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
        payment = consumer.create(payment);
        assertTrue(payment.getId() > 0);
        assertEquals(payment.getAmount(), 5.67, 0);
        assertEquals(payment.getDescription(), "test one");
        consumer.waitUntilFirstMessage();
        List<Shipment> shipments = consumer.getShipments();
        assertEquals(1, shipments.size());
        Shipment shipment = shipments.get(0);
        assertEquals(payment.getId(), shipment.getPaymentId());
        assertEquals("shipped", shipment.getStatus());
    }
    
    @AfterClass
    public static void afterClass() {
        PaymentService.stop(context);
        consumer.stopQueueConsumer();
    }
    
}
