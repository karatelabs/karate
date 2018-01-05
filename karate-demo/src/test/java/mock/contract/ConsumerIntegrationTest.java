package mock.contract;

import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 *
 * @author pthomas3
 */
public class ConsumerIntegrationTest {
    
    private static Consumer consumer;
    
    @BeforeClass
    public static void beforeClass() {
        int port = PaymentService.start();
        String paymentServiceUrl = "http://localhost:" + port;
        consumer = new Consumer(paymentServiceUrl, "DEMO.SHIPPING");       
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
        PaymentService.stop();
        consumer.stopQueueConsumer();
    }
    
}
