package mock.contract;

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
        consumer = new Consumer(paymentServiceUrl);        
    }
    
    @Test
    public void testConsumerIntegration() {
        boolean payment = consumer.getPayment();        
        assertTrue(payment);        
    }
    
    @AfterClass
    public static void afterClass() {
        PaymentService.stop();
    }
    
}
