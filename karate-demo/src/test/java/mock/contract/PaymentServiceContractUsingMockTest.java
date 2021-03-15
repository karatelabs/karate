package mock.contract;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.MockServer;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class PaymentServiceContractUsingMockTest {

    static MockServer server;
    static String queueName = "DEMO.CONTRACT.MOCK";

    @BeforeClass
    public static void beforeClass() {
        server = MockServer
                .feature("classpath:mock/contract/payment-service-mock.feature")
                .arg("queueName", queueName)
                .http(0).build();
    }
    
    @Test
    public void testPaymentService() {
        String paymentServiceUrl = "http://localhost:" + server.getPort();      
        Results results = Runner.path("classpath:mock/contract/payment-service.feature")
                .configDir("classpath:mock/contract")
                .systemProperty("payment.service.url", paymentServiceUrl)
                .systemProperty("shipping.queue.name", queueName)
                .parallel(1);
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);        
    }     

    @AfterClass
    public static void afterClass() {
        server.stop();
    }

}
