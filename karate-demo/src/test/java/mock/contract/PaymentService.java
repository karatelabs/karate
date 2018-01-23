package mock.contract;

import com.intuit.karate.JsonUtils;
import com.intuit.karate.demo.config.ServerStartedInitializingBean;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    @Value("${queue.name}")
    private String queueName;

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
            QueueUtils.send(queueName, JsonUtils.toJson(shipment), 25);
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

    public static ConfigurableApplicationContext start(String queueName, boolean ssl) {
        Stream<String> args = Stream.of("--server.port=0", "--queue.name=" + queueName);
        if (ssl) {
            args = Stream.concat(args, Stream.of(
                    "--server.ssl.key-store=src/test/java/server-keystore.p12",
                    "--server.ssl.key-store-password=karate-mock",
                    "--server.ssl.keyStoreType=PKCS12",
                    "--server.ssl.keyAlias=karate-mock"));
        }
        return SpringApplication.run(PaymentService.class, args.toArray(String[]::new));
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
