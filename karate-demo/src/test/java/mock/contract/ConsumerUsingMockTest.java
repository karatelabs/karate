package mock.contract;

import com.intuit.karate.JsonUtils;
import com.intuit.karate.core.MockServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class ConsumerUsingMockTest {
    
    static final Logger logger = LoggerFactory.getLogger(ConsumerUsingMockTest.class);
    
    static MockServer server;
    static Consumer consumer;
    
    @BeforeAll
    static void beforeAll() {
        String queueName = "DEMO.MOCK";
        server = MockServer
                .feature("classpath:mock/contract/payment-service-mock.feature")
                .arg("queueName", queueName)
                .http(0).build();          
        String paymentServiceUrl = "http://localhost:" + server.getPort();
        consumer = new Consumer(paymentServiceUrl, queueName);        
    }    
    
    @Test
    void testPaymentCreate() throws Exception {
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
    
    @AfterAll
    static void afterAll() {
        server.stop();
        consumer.stopQueueConsumer();
    }    
    
}
