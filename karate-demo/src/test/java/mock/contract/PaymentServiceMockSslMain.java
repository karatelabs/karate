package mock.contract;

import com.intuit.karate.FileUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import java.util.Collections;

/**
 *
 * @author pthomas3
 */
public class PaymentServiceMockSslMain {
    
    public static void main(String[] args) {
        File mockFeatureFile = FileUtils.getFileRelativeTo(PaymentServiceMockSslMain.class, "payment-service-mock.feature");
        File certFile = new File("src/test/java/mock-cert.pem");
        File privateKeyFile = new File("src/test/java/mock-key.pem");
        FeatureServer server = FeatureServer.start(mockFeatureFile, 8443, true, certFile, privateKeyFile, Collections.singletonMap("queueName", "DEMO.MOCK.8443"));
        server.waitSync();
    }
    
}
