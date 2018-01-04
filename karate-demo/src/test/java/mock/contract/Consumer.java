package mock.contract;

import com.intuit.karate.JsonUtils;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author pthomas3
 */
public class Consumer {

    private final String paymentServiceUrl;
    private final String proxyHost;
    private final Integer proxyPort;

    public Consumer(String paymentServiceUrl) {
        this(paymentServiceUrl, null, null);
    }

    public Consumer(String paymentServiceUrl, String proxyHost, Integer proxyPort) {
        this.paymentServiceUrl = paymentServiceUrl;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    private HttpURLConnection getConnection(String path) throws Exception {
        URL url = new URL(paymentServiceUrl + path);
        if (proxyHost != null) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            return (HttpURLConnection) url.openConnection(proxy);
        } else {
            return (HttpURLConnection) url.openConnection();
        }
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
            String content = IOUtils.toString(con.getInputStream(), "utf-8");
            return JsonUtils.fromJson(content, Payment.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private Connection jmsConnection;

    public void startQueueListener(MessageListener ml) {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");
            jmsConnection = connectionFactory.createConnection();
            jmsConnection.start();
            Session session = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue("DEMO.SHIPPING");
            MessageConsumer consumer = session.createConsumer(destination);
            consumer.setMessageListener(ml);
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }
    
    public void stopQueueListener() {
        try {
            jmsConnection.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
