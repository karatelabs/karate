package com.intuit.karate.http;

import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValue.Type;
import static com.intuit.karate.http.HttpClient.*;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author pthomas3
 */
public class HttpUtils {

    private static final String[] PRINTABLES = {"json", "xml", "text", "urlencoded", "html"};
    
    private HttpUtils() {
        // only static methods
    }    

    public static SSLContext getSslContext(String algorithm) {
        TrustManager[] certs = new TrustManager[]{new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                
            }
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                
            }
        }};
        SSLContext ctx = null;
        if (algorithm == null) {            
            algorithm = "TLS";
        }
        try {
            ctx = SSLContext.getInstance(algorithm);
            ctx.init(null, certs, new SecureRandom());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }        
        return ctx;
    }
    
    public static boolean isPrintable(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        String type = mediaType.toLowerCase();
        for (String temp : PRINTABLES) {
            if (type.contains(temp)) {
                return true;
            }
        }
        return false;
    }  
    
    public static String getContentType(ScriptValue sv) {
        switch (sv.getType()) {
            case JSON:
                return APPLICATION_JSON;
            case XML:
                return APPLICATION_XML;
            case INPUT_STREAM:
                return APPLICATION_OCTET_STREAM;
            default:
                return TEXT_PLAIN;
        }
    }
    
    private static final AtomicInteger BOUNDARY_COUNTER = new AtomicInteger();

    public static String generateMimeBoundaryMarker() {;
        StringBuilder sb = new StringBuilder("boundary_");
        sb.append(BOUNDARY_COUNTER.incrementAndGet()).append('_');
        sb.append(System.currentTimeMillis());
        return sb.toString();
    }    
    
    public static String multiPartToString(List<MultiPartItem> items, String boundary) {
        StringBuilder sb = new StringBuilder();
        boolean firstItem = true;
        for (MultiPartItem item : items) {
            if (firstItem) {
                firstItem = false;
                sb.append("--");
            } else {
                sb.append("\r\n--");
            }
            sb.append(boundary);
            sb.append("\r\n");
            ScriptValue sv = item.getValue();
            String contentType = getContentType(sv);
            sb.append("Content-Type: ").append(contentType);
            sb.append("\r\n");
            String name = item.getName();
            if (name != null) {
                sb.append("Content-Disposition: form-data");
                if (sv.getType() == Type.INPUT_STREAM) {
                    sb.append("; filename=\"").append(name).append("\"");
                }
                sb.append("; name=\"").append(name).append("\"");
                sb.append("\r\n");
            }
            sb.append("\r\n");
            if (sv.getType() == Type.INPUT_STREAM) {
                InputStream is = sv.getValue(InputStream.class);
                try {
                    byte[] bytes = IOUtils.toByteArray(is);
                    sb.append(new String(bytes, "utf-8"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                sb.append(sv.getAsString());
            }
        }
        sb.append("\r\n--");
        sb.append(boundary);
        sb.append("--\r\n");
        return sb.toString();
    }    

}
