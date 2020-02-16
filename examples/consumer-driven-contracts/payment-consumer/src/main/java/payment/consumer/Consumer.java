package payment.consumer;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import payment.producer.Payment;

/**
 *
 * @author pthomas3
 */
public class Consumer {

    private static final Logger logger = LoggerFactory.getLogger(Consumer.class);

    private final String paymentServiceUrl;

    public Consumer(String paymentServiceUrl) {
        this.paymentServiceUrl = paymentServiceUrl;
    }

    private HttpURLConnection getConnection(String path) throws Exception {
        URL url = new URL(paymentServiceUrl + path);
        return (HttpURLConnection) url.openConnection();
    }

    public Payment create(Payment payment) {
        try {
            HttpURLConnection con = getConnection("/payments");
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            String json = JsonUtils.toJson(payment);
            IOUtils.write(json, con.getOutputStream(), "utf-8");
            int status = con.getResponseCode();
            if (status != 200) {
                throw new RuntimeException("status code was " + status);
            }
            String content = FileUtils.toString(con.getInputStream());
            return JsonUtils.fromJson(content, Payment.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
