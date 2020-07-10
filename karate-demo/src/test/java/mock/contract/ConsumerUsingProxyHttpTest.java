package mock.contract;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import java.util.Collections;
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
public class ConsumerUsingProxyHttpTest {
    
    private static ConfigurableApplicationContext context;
    private static FeatureServer server;
    private static Consumer consumer;
    
    @BeforeClass
    public static void beforeClass() {
        // actual service
        String queueName = "DEMO.PROXY.HTTP";       
        context = PaymentService.start(queueName, false);        
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);        
        // proxy
        File file = FileUtils.getFileRelativeTo(ConsumerUsingProxyHttpTest.class, "payment-service-proxy.feature");        
        // setting 'paymentServiceUrl' to null uses request url as-is (no re-writing) - so acts as an http proxy
        Map config = Collections.singletonMap("paymentServiceUrl", null);
        server = FeatureServer.start(file, 0, false, config);
        // consumer (using http proxy)
        consumer = new Consumer(paymentServiceUrl, "localhost", server.getPort(), queueName);        
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
        server.stop();
        PaymentService.stop(context);
        consumer.stopQueueConsumer();
    }    
    
}
