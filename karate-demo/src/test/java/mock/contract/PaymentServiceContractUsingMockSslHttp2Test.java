package mock.contract;

import com.intuit.karate.FileUtils;
import com.intuit.karate.junit4.Karate;
import com.intuit.karate.netty.FeatureServer;
import com.intuit.karate.KarateOptions;
import java.io.File;
import java.util.Collections;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:mock/contract/payment-service-http2.feature")
public class PaymentServiceContractUsingMockSslHttp2Test {

    private static FeatureServer server;

    @BeforeClass
    public static void beforeClass() {
        String queueName = "DEMO.CONTRACT.MOCK.SSL.HTTP2";
        System.setProperty("karate.env", "contract");
        File file = FileUtils.getFileRelativeTo(PaymentServiceContractUsingMockSslHttp2Test.class, "payment-service-mock-http2-fallback.feature");
        server = FeatureServer.start(file, 0, true, Collections.singletonMap("queueName", queueName));
        String paymentServiceUrl = "https://localhost:" + server.getPort();
        System.setProperty("payment.service.url", paymentServiceUrl);
        System.setProperty("shipping.queue.name", queueName);
    }

    @AfterClass
    public static void afterClass() {
        server.stop();
    }

}
