package payment.mock.servlet;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;
import payment.producer.PaymentService;

/**
 *
 * @author pthomas3
 */
public class PaymentMockTest {
    
    @Test
    public void testMock() {
        System.setProperty("karate.env", "mock");        
        Results results = Runner.path("classpath:payment/producer").parallel(1);
        assertTrue("there are scenario failures", results.getFailCount() == 0);
    }
    
}
