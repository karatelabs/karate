package mock.contract;

import com.intuit.karate.FileUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
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
        File file = FileUtils.getFileRelativeTo(ConsumerUsingMockTest.class, "payment-service-mock.feature");
        server = FeatureServer.start(file, 0, false, null);        
        String paymentServiceUrl = "http://localhost:" + server.getPort();
        consumer = new Consumer(paymentServiceUrl);        
    }    
    
    @Test
    public void testConsumerIntegration() {
        boolean payment = consumer.getPayment();
        assertTrue(payment);        
    }
    
    @AfterClass
    public static void afterClass() {
        server.stop();
    }    
    
}
