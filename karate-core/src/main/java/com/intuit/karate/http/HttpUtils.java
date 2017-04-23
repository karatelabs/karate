package com.intuit.karate.http;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLContext;

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

}
