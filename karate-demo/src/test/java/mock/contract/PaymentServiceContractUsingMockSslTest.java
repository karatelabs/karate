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
class PaymentServiceContractUsingMockSslTest {

    static MockServer server;
    static String queueName = "DEMO.CONTRACT.MOCK.SSL";

    @BeforeAll
    static void beforeAll() {
        server = MockServer
                .feature("classpath:mock/contract/payment-service-mock.feature")
                .arg("queueName", queueName)
                .https(0).build();
    }
    
    // @Test // TODO jdk 17
    void testPaymentService() {
        String paymentServiceUrl = "https://localhost:" + server.getPort();      
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
