package payment.producer.contract;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import payment.producer.PaymentService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:payment/producer/contract/payment-contract.feature")
public class PaymentContractTest {
    
    private static ConfigurableApplicationContext context;
    
    @BeforeClass
    public static void beforeClass() {
        context = PaymentService.start();
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);
        System.setProperty("payment.service.url", paymentServiceUrl);
    }
    
    @AfterClass
    public static void afterClass() {
        PaymentService.stop(context);
    }    
    
}
