package mock.contract;

import com.intuit.karate.junit4.Karate;
import com.intuit.karate.KarateOptions;
import com.intuit.karate.runtime.MockServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
// @RunWith(Karate.class)
@KarateOptions(features = "classpath:mock/contract/payment-service.feature")
public class PaymentServiceContractUsingMockTest {

    private static MockServer server;

    @BeforeClass
    public static void beforeClass() {
        String queueName = "DEMO.CONTRACT.MOCK";
        System.setProperty("karate.env", "contract");
        server = MockServer
                .feature("classpath:mock/contract/payment-service-mock.feature")
                .arg("queueName", queueName)
                .http(0).build();
        String paymentServiceUrl = "http://localhost:" + server.getPort();
        System.setProperty("payment.service.url", paymentServiceUrl);
        System.setProperty("shipping.queue.name", queueName);
    }

    @AfterClass
    public static void afterClass() {
        server.stop();
    }

}
