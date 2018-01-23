package ssl;

import com.intuit.karate.demo.config.ServerStartedInitializingBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;

/**
 *
 * @author pthomas3
 */
@Configuration
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class})
public class TestService {

    private static final Logger logger = LoggerFactory.getLogger(TestService.class);

    @RestController
    @RequestMapping("/test")
    class TestController {

        @GetMapping
        public String test() {
            return "{ \"success\": true }";
        }

    }

    public static ConfigurableApplicationContext start() {
        String[] args = {
            "--server.port=0",
            "--server.ssl.key-store=src/test/java/server-keystore.p12",
            "--server.ssl.key-store-password=karate-mock",
            "--server.ssl.keyStoreType=PKCS12",
            "--server.ssl.keyAlias=karate-mock"};
        return SpringApplication.run(TestService.class, args);
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
