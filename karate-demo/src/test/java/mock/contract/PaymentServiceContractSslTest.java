package mock.contract;

import com.intuit.karate.junit4.Karate;
import com.intuit.karate.KarateOptions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:mock/contract/payment-service.feature")
public class PaymentServiceContractSslTest {
    
    private static ConfigurableApplicationContext context;
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "contract");        
        String queueName = "DEMO.CONTRACT.SSL";
        context = PaymentService.start(queueName, true);
        String paymentServiceUrl = "https://localhost:" + PaymentService.getPort(context);
        System.setProperty("payment.service.url", paymentServiceUrl);
        System.setProperty("shipping.queue.name", queueName);
    }
    
    @AfterClass
    public static void afterClass() {
        PaymentService.stop(context);
    }
    
}
