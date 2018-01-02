package mock.contract;

import com.intuit.karate.junit4.Karate;
import cucumber.api.CucumberOptions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@CucumberOptions(features = "classpath:mock/contract/payment-service.feature")
public class PaymentServiceContractTest {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "contract");
        int port = PaymentService.start();
        String paymentServiceUrl = "http://localhost:" + port;
        System.setProperty("payment.service.url", paymentServiceUrl);       
    }
    
    @AfterClass
    public static void afterClass() {
        PaymentService.stop();
    }
    
}
