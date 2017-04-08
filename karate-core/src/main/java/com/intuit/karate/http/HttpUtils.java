package com.intuit.karate.http;

import com.intuit.karate.Script;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValue;
import com.jayway.jsonpath.DocumentContext;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.util.Map;
import javax.net.ssl.SSLContext;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    private HttpUtils() {
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
    
    public static Map<String, Object> evalConfiguredHeaders(ScriptContext context) {
        ScriptValue headersValue = context.getConfiguredHeaders();
        switch (headersValue.getType()) {
            case JS_FUNCTION:
                ScriptObjectMirror som = headersValue.getValue(ScriptObjectMirror.class);
                ScriptValue sv = Script.evalFunctionCall(som, null, context);
                switch (sv.getType()) {
                    case JS_OBJECT:
                        return Script.toMap(sv.getValue(ScriptObjectMirror.class));
                    case MAP:
                        return sv.getValue(Map.class);
                    default:
                        logger.trace("custom headers function returned: {}", sv);
                        return null;
                }
            case JSON:
                DocumentContext json = headersValue.getValue(DocumentContext.class);
                return json.read("$");
            default:
                logger.trace("configured 'headers' is not a map-like object or js function: {}", headersValue);
                return null;
        }        
    }    

}
