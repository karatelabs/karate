package demo.headers;

import com.intuit.karate.http.HttpLogModifier;

/**
 *
 * @author pthomas3
 */
public class DemoLogModifier implements HttpLogModifier {
    
    public static final HttpLogModifier INSTANCE = new DemoLogModifier();

    @Override
    public boolean enableForUri(String uri) {
        return uri.contains("/headers");
    }

    @Override
    public String uri(String uri) {
        return uri;
    }        

    @Override
    public String header(String header, String value) {
        if (header.toLowerCase().contains("xss-protection")) {
            return "***";
        }
        return value;
    }

    @Override
    public String request(String uri, String request) {
        return request;
    }

    @Override
    public String response(String uri, String response) {
        // you can use a regex and find and replace if needed
        return "***";
    }

}
