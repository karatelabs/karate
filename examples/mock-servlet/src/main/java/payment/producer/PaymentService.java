package payment.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author pthomas3
 */
@Configuration
@EnableAutoConfiguration
public class PaymentService {

    @Bean
    public PaymentController getController() {
        return new PaymentController();
    }

    public static ConfigurableApplicationContext start() {
        return SpringApplication.run(PaymentService.class, new String[]{"--server.port=0"});
    }

    public static void stop(ConfigurableApplicationContext context) {
        SpringApplication.exit(context, () -> 0);
    }

    public static int getPort(ConfigurableApplicationContext context) {
        ServerStartedInitializingBean ss = context.getBean(ServerStartedInitializingBean.class);
        return ss.getLocalPort();
    }

    @Bean
    public ServerStartedInitializingBean getInitializingBean() {
        return new ServerStartedInitializingBean();
    }

}
