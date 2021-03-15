package mock.contract;

import com.intuit.karate.core.MockServer;
import java.io.File;

/**
 *
 * @author pthomas3
 */
public class PaymentServiceMockSslMain {

    public static void main(String[] args) {
        File certFile = new File("src/test/java/mock-cert.pem");
        File privateKeyFile = new File("src/test/java/mock-key.pem");
        MockServer server = MockServer
                .feature("classpath:mock/contract/payment-service-mock.feature")
                .certFile(certFile)
                .keyFile(privateKeyFile)
                .arg("queueName", "DEMO.MOCK.8443")
                .https(8443).build();
        server.waitSync();
    }

}
