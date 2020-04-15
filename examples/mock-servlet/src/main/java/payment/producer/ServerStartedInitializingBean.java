package payment.producer;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 *
 * @author pthomas3
 */
@Component
public class ServerStartedInitializingBean implements ApplicationRunner, ApplicationListener<WebServerInitializedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ServerStartedInitializingBean.class);

    private int localPort;

    public int getLocalPort() {
        return localPort;
    }

    @Override
    public void run(ApplicationArguments aa) throws Exception {
        logger.info("server started with args: {}", Arrays.toString(aa.getSourceArgs()));
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent e) {
        localPort = e.getWebServer().getPort();
        logger.info("after runtime init, local server port: {}", localPort);
    }

}
