package mock.contract;

import com.intuit.karate.FileUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 *
 * @author pthomas3
 */
public class ConsumerUsingProxyHttpTest {
    
    private static FeatureServer server;
    private static Consumer consumer;
    
    @BeforeClass
    public static void beforeClass() {
        // actual service        
        int port = PaymentService.start();        
        String paymentServiceUrl = "http://localhost:" + port;        
        // proxy
        File file = FileUtils.getFileRelativeTo(ConsumerUsingProxyHttpTest.class, "payment-service-proxy.feature");        
        // setting this to null uses request URL as-is (no re-writing) - so acts as an http proxy
        Map config = Collections.singletonMap("paymentServiceUrl", null);
        server = FeatureServer.start(file, 0, false, config);
        // consumer (using http proxy)
        consumer = new Consumer(paymentServiceUrl, "localhost", server.getPort());        
    }    
    
    @Test
    public void testConsumerIntegration() {
        boolean payment = consumer.getPayment();
        assertTrue(payment);        
    }
    
    @AfterClass
    public static void afterClass() {
        server.stop();
        PaymentService.stop();
    }    
    
}
