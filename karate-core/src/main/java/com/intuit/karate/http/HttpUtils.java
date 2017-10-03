package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValue.Type;
import com.intuit.karate.StringUtils;
import static com.intuit.karate.http.HttpClient.*;
import java.io.InputStream;
import java.security.SecureRandom;
import javax.net.ssl.TrustManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
        TrustManager[] certs = new TrustManager[]{new LenientTrustManager()};
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
        if (sv.isStream()) {
            return APPLICATION_OCTET_STREAM;
        } else if (sv.getType() == Type.XML) {
            return APPLICATION_XML;
        } else if (sv.isMapLike()) {
            return APPLICATION_JSON;
        } else {
            return TEXT_PLAIN;
        }
    }
    
    public static StringUtils.Pair splitCharsetIfPresent(String mediaType) {
        int pos = mediaType.indexOf(';');
        if (pos == -1) {
            return StringUtils.pair(mediaType, null);
        } else {
            int equalPos = mediaType.lastIndexOf('=');
            String charset = equalPos == -1 ? null : mediaType.substring(equalPos + 1);
            mediaType = mediaType.substring(0, pos);
            return StringUtils.pair(mediaType, charset);
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
                if (item.getFilename() != null) {
                    sb.append("; filename=\"").append(item.getFilename()).append("\"");
                }
                sb.append("; name=\"").append(name).append("\"");
                sb.append("\r\n");
            }
            sb.append("\r\n");
            if (sv.getType() == Type.INPUT_STREAM) {
                InputStream is = sv.getValue(InputStream.class);
                String bytes = FileUtils.toString(is);
                sb.append(bytes);
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
