package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValue.Type;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import static com.intuit.karate.http.HttpClient.*;
import com.jayway.jsonpath.DocumentContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import javax.net.ssl.TrustManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import org.w3c.dom.Document;

/**
 *
 * @author pthomas3
 */
public class HttpUtils {

    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_CONTENT_LENGTH = "Content-Length";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_COOKIE = "Cookie";
    public static final String HEADER_HOST = "Host";

    private static final String[] PRINTABLES = {"json", "xml", "text", "urlencoded", "html"};

    public static final Set<String> HTTP_METHODS
            = Stream.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "CONNECT", "TRACE")
                    .collect(Collectors.toSet());

    private HttpUtils() {
        // only static methods
    }
    
    public static void updateResponseVars(HttpResponse response, ScriptValueMap vars, ScriptContext context) {
        vars.put(ScriptValueMap.VAR_RESPONSE_STATUS, response.getStatus());
        vars.put(ScriptValueMap.VAR_RESPONSE_TIME, response.getTime());
        vars.put(ScriptValueMap.VAR_RESPONSE_COOKIES, response.getCookies());
        vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, response.getHeaders());
        Object responseBody = convertResponseBody(response.getBody(), context);
        if (responseBody instanceof String) {
            String responseString = StringUtils.trimToEmpty((String) responseBody);
            if (Script.isJson(responseString)) {
                try {
                    DocumentContext doc = JsonUtils.toJsonDoc(responseString);
                    responseBody = doc;
                    if (context.isLogPrettyResponse()) {
                        context.logger.info("response:\n{}", JsonUtils.toPrettyJsonString(doc));
                    }
                } catch (Exception e) {
                    context.logger.warn("json parsing failed, response data type set to string: {}", e.getMessage());
                }
            } else if (Script.isXml(responseString)) {
                try {
                    Document doc = XmlUtils.toXmlDoc(responseString);
                    responseBody = doc;
                    if (context.isLogPrettyResponse()) {
                        context.logger.info("response:\n{}", XmlUtils.toString(doc, true));
                    }
                } catch (Exception e) {
                    context.logger.warn("xml parsing failed, response data type set to string: {}", e.getMessage());
                }
            }
        }
        vars.put(ScriptValueMap.VAR_RESPONSE, responseBody);        
    }
    
    private static Object convertResponseBody(byte[] bytes, ScriptContext context) {
        if (bytes == null) {
            return null;
        }
        // if a byte array contains a negative-signed byte,
        // then the string conversion will corrupt it.
        // in that case just return the byte array stream
        try {
            String rawString = FileUtils.toString(bytes);
            if (Arrays.equals(bytes, rawString.getBytes())) {
                return rawString;
            }
        } catch (Exception e) {
            context.logger.warn("response bytes to string conversion failed: {}", e.getMessage());
        }
        return new ByteArrayInputStream(bytes);
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
        } else if (sv.isJsonLike()) {
            return APPLICATION_JSON;
        } else {
            return TEXT_PLAIN;
        }
    }
    
    public static StringUtils.Pair parseUriIntoUrlBaseAndPath(String rawUri) {
        try {
            URI uri = new URI(rawUri);
            String host = uri.getHost();
            if (host == null) {
                return StringUtils.pair(null, rawUri);
            }
            String path = uri.getRawPath();
            int pos = rawUri.indexOf(path);
            String urlBase = rawUri.substring(0, pos);
            return StringUtils.pair(urlBase, rawUri.substring(pos));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Cookie> parseCookieHeaderString(String header) {
        List<HttpCookie> list = HttpCookie.parse(header);
        Map<String, Cookie> map = new HashMap(list.size());
        list.forEach((hc) -> {
            String name = hc.getName();
            Cookie c = new Cookie(name, hc.getValue());
            c.putIfValueNotNull(Cookie.DOMAIN, hc.getDomain());
            c.putIfValueNotNull(Cookie.PATH, hc.getPath());
            c.putIfValueNotNull(Cookie.VERSION, hc.getVersion() + "");
            c.putIfValueNotNull(Cookie.MAX_AGE, hc.getMaxAge() + "");
            c.putIfValueNotNull(Cookie.SECURE, hc.getSecure() + "");
            map.put(name, c);
        });
        return map;
    }
    
    public static String createCookieHeaderValue(Collection<Cookie> cookies) {
        return cookies.stream()
                .map((c) -> c.getName() + "=" + c.getValue())
                .collect(Collectors.joining("; "));
    }

    public static Map<String, String> parseUriPattern(String pattern, String url) {
        int qpos = url.indexOf('?');
        if (qpos != -1) {
            url = url.substring(0, qpos);
        }
        List<String> leftList = StringUtils.split(pattern, '/');
        List<String> rightList = StringUtils.split(url, '/');
        int leftSize = leftList.size();
        int rightSize = rightList.size();
        if (rightSize != leftSize) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap(leftSize);
        for (int i = 0; i < leftSize; i++) {
            String left = leftList.get(i);
            String right = rightList.get(i);
            if (left.equals(right)) {
                continue;
            }
            if (left.startsWith("{") && left.endsWith("}")) {
                left = left.substring(1, left.length() - 1);
                map.put(left, right);
            } else {
                return null; // match failed
            }
        }
        return map;
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
