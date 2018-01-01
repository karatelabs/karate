package mock.contract;

import com.intuit.karate.demo.config.ServerStartedInitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author pthomas3
 */
@Configuration
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class})
public class PaymentService {
    
    @RestController
    class PaymentController {
        
        @RequestMapping("/pay")
        public String pay() {
            return "{ \"success\": true }";
        }
        
    }
    
    private static ConfigurableApplicationContext context;
	
	public static int start() {
        if (context == null) {
            context = SpringApplication.run(PaymentService.class, new String[]{"--server.port=0"});
        }
        ServerStartedInitializingBean ss = context.getBean(ServerStartedInitializingBean.class);
        return ss.getLocalPort();        
	}
    
    public static void stop() {
        context.stop();
    }
    
    @Bean
    public ServerStartedInitializingBean getInitializingBean() {
        return new ServerStartedInitializingBean();
    }    
    
}
