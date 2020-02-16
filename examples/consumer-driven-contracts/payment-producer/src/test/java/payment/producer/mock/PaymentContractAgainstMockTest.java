package payment.producer.mock;

import com.intuit.karate.FileUtils;
import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:payment/producer/contract/payment-contract.feature")
public class PaymentContractAgainstMockTest {
    
    private static FeatureServer server;
    
    @BeforeClass
    public static void beforeClass() {       
        File file = FileUtils.getFileRelativeTo(PaymentContractAgainstMockTest.class, "payment-mock.feature");
        server = FeatureServer.start(file, 0, false, null);
        String paymentServiceUrl = "http://localhost:" + server.getPort();
        System.setProperty("payment.service.url", paymentServiceUrl);
    }
        
    @AfterClass
    public static void afterClass() {
        server.stop();        
    }     
    
}
