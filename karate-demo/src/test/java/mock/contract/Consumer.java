package mock.contract;

import com.jayway.jsonpath.JsonPath;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;
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

    public boolean getPayment() {
        try {
            URL url = new URL(paymentServiceUrl + "/pay");
            HttpURLConnection con;
            if (proxyHost != null) {                 
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
                con = (HttpURLConnection) url.openConnection(proxy);
            } else {
                con = (HttpURLConnection) url.openConnection();  
            }
            con.setRequestMethod("GET");
            int status = con.getResponseCode();
            if (status != 200) {
                throw new RuntimeException("status code was " + status);
            }
            String content = IOUtils.toString(con.getInputStream(), "utf-8");
            Map map = JsonPath.parse(content).read("$");
            return (boolean) map.get("success");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
