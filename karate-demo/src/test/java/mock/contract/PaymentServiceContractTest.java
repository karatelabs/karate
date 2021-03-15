package mock.contract;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
public class PaymentServiceContractTest {
    
    static ConfigurableApplicationContext context;
    static String queueName = "DEMO.CONTRACT";
    
    @BeforeClass
    public static void beforeClass() {
        context = PaymentService.start(queueName, false);
    }
    
    @Test
    public void testPaymentService() {
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);      
        Results results = Runner.path("classpath:mock/contract/payment-service.feature")
                .configDir("classpath:mock/contract")
                .systemProperty("payment.service.url", paymentServiceUrl)
                .systemProperty("shipping.queue.name", queueName)
                .parallel(1);
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);        
    }    
    
    @AfterClass
    public static void afterClass() {
        PaymentService.stop(context);
    }
    
}
