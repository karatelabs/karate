package mock.contract;

import com.intuit.karate.JsonUtils;
import com.intuit.karate.demo.config.ServerStartedInitializingBean;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
            Shipment shipment = new Shipment();
            shipment.setPaymentId(id);
            shipment.setStatus("shipped");            
            QueueUtils.send("DEMO.SHIPPING", JsonUtils.toJson(shipment), 25);
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
