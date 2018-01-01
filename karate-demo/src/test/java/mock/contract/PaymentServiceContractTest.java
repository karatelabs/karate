package mock.contract;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import cucumber.api.CucumberOptions;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:mock/contract/payment-service.feature")
public class PaymentServiceContractTest {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "contract");
        int port = PaymentService.start();
        String paymentServiceUrl = "http://localhost:" + port;
        System.setProperty("payment.service.url", paymentServiceUrl);       
    }    
    
    @Test
    public void testPaymentService() {
        KarateStats stats = CucumberRunner.parallel(getClass(), 1, "target/contract/payment-service");        
        assertTrue("there are scenario failures", stats.getFailCount() == 0);        
    }
    
    @AfterClass
    public static void afterClass() {
        PaymentService.stop();
    }
    
}
