package mock.contract;

import com.intuit.karate.FileUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import java.util.Collections;

/**
 *
 * @author pthomas3
 */
public class PaymentServiceMockMain {
    
    public static void main(String[] args) {
        File file = FileUtils.getFileRelativeTo(PaymentServiceMockMain.class, "payment-service-mock.feature");
        FeatureServer server = FeatureServer.start(file, 8080, false, Collections.singletonMap("queueName", "DEMO.MOCK.8080"));
        server.waitSync();
    }
    
}
