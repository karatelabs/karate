package mock.contract;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import java.util.Collections;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 *
 * @author pthomas3
 */
public class ConsumerUsingMockTest {
    
    private static FeatureServer server;
    private static Consumer consumer;
    
    @BeforeClass
    public static void beforeClass() {
        String queueName = "DEMO.MOCK";
        File file = FileUtils.getFileRelativeTo(ConsumerUsingMockTest.class, "payment-service-mock.feature");
        server = FeatureServer.start(file, 0, false, Collections.singletonMap("queueName", queueName));
        String paymentServiceUrl = "http://localhost:" + server.getPort();
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
            wait();
        }       
    }
    
    @AfterClass
    public static void afterClass() {
        server.stop();
        consumer.stopQueueConsumer();
    }    
    
}
