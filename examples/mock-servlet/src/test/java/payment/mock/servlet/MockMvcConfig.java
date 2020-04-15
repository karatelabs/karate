package payment.mock.servlet;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payment.producer.PaymentController;

/**
 *
 * @author pthomas3
 */
@Configuration
@EnableAutoConfiguration
public class MockMvcConfig {

    @Bean
    public PaymentController getController() {
        return new PaymentController();
    }

}
