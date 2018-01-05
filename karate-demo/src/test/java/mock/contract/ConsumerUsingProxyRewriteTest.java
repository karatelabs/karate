package mock.contract;

import com.intuit.karate.FileUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
public class ConsumerUsingProxyRewriteTest {
    
    private static ConfigurableApplicationContext context;
    private static FeatureServer server;
    private static Consumer consumer;
    
    @BeforeClass
    public static void beforeClass() {
        // actual service      
        String queueName = "DEMO.PROXY.REWRITE";       
        context = PaymentService.start(queueName);
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);
        // proxy
        File file = FileUtils.getFileRelativeTo(ConsumerUsingProxyRewriteTest.class, "payment-service-proxy.feature");                        
        Map config = Collections.singletonMap("paymentServiceUrl", paymentServiceUrl);
        // requests will be forwarded / url re-written to paymentServiceUrl
        server = FeatureServer.start(file, 0, false, config);
        // consumer
        String proxyUrl = "http://localhost:" + server.getPort();        
        consumer = new Consumer(proxyUrl, queueName);        
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
        consumer.waitUntilFirstMessage();
        List<Shipment> shipments = consumer.getShipments();
        assertEquals(1, shipments.size());
        Shipment shipment = shipments.get(0);
        assertEquals(payment.getId(), shipment.getPaymentId());
        assertEquals("shipped", shipment.getStatus());        
    }
    
    @AfterClass
    public static void afterClass() {
        server.stop();
        PaymentService.stop(context);
        consumer.stopQueueConsumer();
    }    
    
}
