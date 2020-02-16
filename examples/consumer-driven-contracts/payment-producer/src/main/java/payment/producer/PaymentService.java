package payment.producer;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author pthomas3
 */
@Configuration
@EnableAutoConfiguration
public class PaymentService {
    
    @RestController
    @RequestMapping("/payments")    
    class PaymentController {
        
        private final AtomicInteger counter = new AtomicInteger();
        private final Map<Integer, Payment> payments = new ConcurrentHashMap();

        @PostMapping
        public Payment create(@RequestBody Payment payment) {
            int id = counter.incrementAndGet();
            payment.setId(id);
            payments.put(id, payment);
            return payment;
        }

        @PutMapping("/{id:.+}")
        public Payment update(@PathVariable int id, @RequestBody Payment payment) {
            payments.put(id, payment);
            return payment;
        }

        @GetMapping
        public Collection<Payment> list() {
            return payments.values();
        }

        @GetMapping("/{id:.+}")
        public Payment get(@PathVariable int id) {
            return payments.get(id);
        }

        @DeleteMapping("/{id:.+}")
        public void delete(@PathVariable int id) {
            Payment payment = payments.remove(id);
            if (payment == null) {
                throw new RuntimeException("payment not found, id: " + id);
            }
        }        
        
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
