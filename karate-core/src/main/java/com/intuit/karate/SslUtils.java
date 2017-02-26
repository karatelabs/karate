package com.intuit.karate;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class SslUtils {

    private static final Logger logger = LoggerFactory.getLogger(SslUtils.class);

    private SslUtils() {
        // only static methods
    }

    public static SSLContext getSslContext(String algorithm) {
        TrustManager[] certs = new TrustManager[]{new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                logger.trace("get accepted issuers");
                return new X509Certificate[0];
            }
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                logger.trace("check server trusted");
            }
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                logger.trace("check client trusted");
            }
        }};
        SSLContext ctx = null;
        if (algorithm == null) {            
            algorithm = "TLS";
            logger.warn("ssl algorithm not set, defaulting to: {}", algorithm);
        }
        try {
            ctx = SSLContext.getInstance(algorithm);
            ctx.init(null, certs, new SecureRandom());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }        
        return ctx;
    }

}
