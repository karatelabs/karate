package mock.contract;

import com.intuit.karate.core.MockServer;

/**
 *
 * @author pthomas3
 */
public class PaymentServiceMockMain {

    public static void main(String[] args) {
        MockServer server = MockServer
                .feature("classpath:mock/contract/payment-service-mock.feature")
                .arg("queueName", "DEMO.MOCK.8080")
                .http(8080).build();
        server.waitSync();
    }

}
