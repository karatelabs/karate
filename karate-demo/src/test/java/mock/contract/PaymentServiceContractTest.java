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
public class PaymentServiceContractTest {
    
    private static ConfigurableApplicationContext context;
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "contract");
        String queueName = "DEMO.CONTRACT";
        context = PaymentService.start(queueName, false);
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);
        System.setProperty("payment.service.url", paymentServiceUrl);
        System.setProperty("shipping.queue.name", queueName);
    }
    
    @AfterClass
    public static void afterClass() {
        PaymentService.stop(context);
    }
    
}
