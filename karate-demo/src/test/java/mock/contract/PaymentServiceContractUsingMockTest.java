package mock.contract;

import com.intuit.karate.FileUtils;
import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import com.intuit.karate.netty.FeatureServer;
import cucumber.api.CucumberOptions;
import java.io.File;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:mock/contract/payment-service.feature")
public class PaymentServiceContractUsingMockTest {
    
    private static FeatureServer server;
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "contract");
        File file = FileUtils.getFileRelativeTo(PaymentServiceContractUsingMockTest.class, "payment-service-mock.feature");
        server = FeatureServer.start(file, 0, false, null);
        String paymentServiceUrl = "http://localhost:" + server.getPort();
        System.setProperty("payment.service.url", paymentServiceUrl);       
    }    
    
    @Test
    public void testPaymentService() {
        KarateStats stats = CucumberRunner.parallel(getClass(), 1, "target/contract/payment-service-mock");        
        assertTrue("there are scenario failures", stats.getFailCount() == 0);        
    }
        
    @AfterClass
    public static void afterClass() {
        server.stop();
    }     
    
}
