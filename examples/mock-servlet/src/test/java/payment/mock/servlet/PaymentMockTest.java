/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
//        ConfigurableApplicationContext context = PaymentService.start();
//        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);
//        System.setProperty("payment.service.url", paymentServiceUrl);        
        Results results = Runner.path("classpath:payment/producer").parallel(1);
        assertTrue("there are scenario failures", results.getFailCount() == 0);
    }
    
}
