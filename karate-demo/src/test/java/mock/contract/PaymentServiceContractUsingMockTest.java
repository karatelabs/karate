package mock.contract;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class PaymentServiceContractUsingMockTest {

    static MockServer server;
    static String queueName = "DEMO.CONTRACT.MOCK";

    @BeforeAll
    static void beforeAll() {
        server = MockServer
                .feature("classpath:mock/contract/payment-service-mock.feature")
                .arg("queueName", queueName)
                .http(0).build();
    }
    
    @Test
    void testPaymentService() {
        String paymentServiceUrl = "http://localhost:" + server.getPort();      
        Results results = Runner.path("classpath:mock/contract/payment-service.feature")
                .configDir("classpath:mock/contract")
                .systemProperty("payment.service.url", paymentServiceUrl)
                .systemProperty("shipping.queue.name", queueName)
                .parallel(1);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());        
    }     

    @AfterAll
    static void afterAll() {
        server.stop();
    }

}
