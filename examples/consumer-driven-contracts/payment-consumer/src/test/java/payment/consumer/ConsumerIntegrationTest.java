package payment.consumer;

import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.springframework.context.ConfigurableApplicationContext;
import payment.producer.Payment;
import payment.producer.PaymentService;

/**
 *
 * @author pthomas3
 */
public class ConsumerIntegrationTest {
    
    private static ConfigurableApplicationContext context;
    private static Consumer consumer;
    
    @BeforeClass
    public static void beforeClass() {
        context = PaymentService.start();
        String paymentServiceUrl = "http://localhost:" + PaymentService.getPort(context);
        consumer = new Consumer(paymentServiceUrl);       
    }
    
    @Test
    public void testPaymentCreate() throws Exception {
        Payment payment = new Payment();
        payment.setAmount(5.67);
        payment.setDescription("test one");
        payment = consumer.create(payment);
        assertTrue(payment.getId() > 0);
        assertEquals(payment.getAmount(), 5.67, 0);
        assertEquals(payment.getDescription(), "test one");
    }
    
    @AfterClass
    public static void afterClass() {
        PaymentService.stop(context);
    }
    
}
