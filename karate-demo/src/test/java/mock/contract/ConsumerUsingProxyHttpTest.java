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
        // setting 'paymentServiceUrl' to null uses request url as-is (no re-writing) - so acts as an http proxy
        Map config = Collections.singletonMap("paymentServiceUrl", null);
        server = FeatureServer.start(file, 0, false, config);
        // consumer (using http proxy)
        consumer = new Consumer(paymentServiceUrl, "localhost", server.getPort());        
    }    
    
    @Test
    public void testPaymentCreate() {
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
        PaymentService.stop();
    }    
    
}
