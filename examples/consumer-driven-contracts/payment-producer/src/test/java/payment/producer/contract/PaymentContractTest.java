package payment.producer.contract;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import payment.producer.PaymentService;

/**
 *
 * @author pthomas3
 */
class PaymentContractTest {

    static ConfigurableApplicationContext context;

    @BeforeAll
    static void beforeAll() {
        context = PaymentService.start();
    }

    @Test
    void testReal() {
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);
        Results results = Runner.path("classpath:payment/producer/contract/payment-contract.feature")
                .systemProperty("payment.service.url", paymentServiceUrl)
                .parallel(1);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }

    @AfterAll
    static void afterAll() {
        PaymentService.stop(context);
    }

}
