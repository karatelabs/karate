package payment.producer.mock;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class PaymentContractAgainstMockTest {

    static MockServer server;

    @BeforeAll
    static void beforeAll() {
        server = MockServer.feature("classpath:payment/producer/mock/payment-mock.feature").http(0).build();
    }

    @Test
    void testMock() {
        String paymentServiceUrl = "http://localhost:" + server.getPort();
        Results results = Runner.path("classpath:payment/producer/contract/payment-contract.feature")
                .systemProperty("payment.service.url", paymentServiceUrl)
                .parallel(1);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

}
