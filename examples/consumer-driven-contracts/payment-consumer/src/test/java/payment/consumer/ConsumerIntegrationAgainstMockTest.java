package payment.consumer;

import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import payment.producer.Payment;

/**
 *
 * @author pthomas3
 */
public class ConsumerIntegrationAgainstMockTest {
    
    private static FeatureServer server;
    private static Consumer consumer;
    
    @BeforeClass
    public static void beforeClass() {
        File file = new File("../payment-producer/src/test/java/payment/producer/mock/payment-mock.feature");
        server = FeatureServer.start(file, 0, false, null);
        String paymentServiceUrl = "http://localhost:" + server.getPort();
        consumer = new Consumer(paymentServiceUrl);        
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
    }
    
    @AfterClass
    public static void afterClass() {
        server.stop();
    }    
    
}
