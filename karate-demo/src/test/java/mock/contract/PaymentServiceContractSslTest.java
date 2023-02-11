package mock.contract;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.springframework.context.ConfigurableApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class PaymentServiceContractSslTest {
    
    static ConfigurableApplicationContext context;
    static String queueName = "DEMO.CONTRACT.SSL";
    
    @BeforeAll
    static void beforeAll() {   
        context = PaymentService.start(queueName, true);
    }
    
    @Test
    void testPaymentService() {
        String paymentServiceUrl = "https://localhost:" + PaymentService.getPort(context);      
        Results results = Runner.path("classpath:mock/contract/payment-service.feature")
                .configDir("classpath:mock/contract")
                .systemProperty("payment.service.url", paymentServiceUrl)
                .systemProperty("shipping.queue.name", queueName)
                .parallel(1);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());        
    }
    
    @AfterAll
    static void afterAll() {
        PaymentService.stop(context);
    }
    
}
