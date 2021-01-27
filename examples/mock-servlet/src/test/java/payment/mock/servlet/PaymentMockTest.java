package payment.mock.servlet;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class PaymentMockTest {
    
    @Test
    public void testMock() {
        System.setProperty("karate.env", "mock");        
        Results results = Runner.path("classpath:payment/producer")
                .systemProperty("karate.env", "mock")
                .clientFactory(new MockSpringMvcServlet())
                .parallel(1);
        assertTrue("there are scenario failures", results.getFailCount() == 0);
    }
    
}
