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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.KeyStore;
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
    public static final String HEADER_ALLOW = "Allow";
    public static final String HEADER_AC_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String HEADER_AC_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String HEADER_AC_REQUEST_HEADERS = "Access-Control-Request-Headers";
    public static final String HEADER_AC_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    
    public static final String CHARSET = "charset";

    private static final String[] PRINTABLES = {"json", "xml", "text", "urlencoded", "html"};

    public static final Set<String> HTTP_METHODS
            = Stream.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "CONNECT", "TRACE")
                    .collect(Collectors.toSet());

    private HttpUtils() {
        // only static methods
    }

    public static void updateResponseVars(HttpResponse response, ScriptValueMap vars, ScriptContext context) {
        vars.put(ScriptValueMap.VAR_RESPONSE_STATUS, response.getStatus());
        vars.put(ScriptValueMap.VAR_REQUEST_TIME_STAMP, response.getStartTime());
        vars.put(ScriptValueMap.VAR_RESPONSE_TIME, response.getResponseTime());
        vars.put(ScriptValueMap.VAR_RESPONSE_COOKIES, response.getCookies());
        vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, response.getHeaders());
        Object responseBody = convertResponseBody(response.getBody(), context);
        if (responseBody instanceof String) {
            String responseString = StringUtils.trimToEmpty((String) responseBody);
            if (Script.isJson(responseString)) {
                try {
                    responseBody = JsonUtils.toJsonDoc(responseString);
                } catch (Exception e) {
                    context.logger.warn("json parsing failed, response data type set to string: {}", e.getMessage());
                }
            } else if (Script.isXml(responseString)) {
                try {
                    responseBody = XmlUtils.toXmlDoc(responseString);
                } catch (Exception e) {
                    context.logger.warn("xml parsing failed, response data type set to string: {}", e.getMessage());
                }
            }
        }
        vars.put(ScriptValueMap.VAR_RESPONSE, responseBody);
    }

    public static void updateRequestVars(HttpRequestBuilder request, ScriptValueMap vars, ScriptContext context) {
        vars.put(ScriptValueMap.VAR_REQUEST_METHOD, request.getMethod());
        vars.put(ScriptValueMap.VAR_REQUEST_URI, request.getUrl());
        vars.put(ScriptValueMap.VAR_REQUEST_PARAMS, request.getParams());
        vars.put(ScriptValueMap.VAR_REQUEST_HEADERS, request.getHeaders());
        vars.put(ScriptValueMap.VAR_REQUEST_BODY, request.getBody());
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

    public static KeyStore getKeyStore(ScriptContext context, String trustStoreFile, String password, String type) {
        if (trustStoreFile == null) {
            return null;
        }
        char[] passwordChars = password == null ? null : password.toCharArray();
        if (type == null) {
            type = KeyStore.getDefaultType();
        }
        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            InputStream is = FileUtils.getFileStream(trustStoreFile, context);
            keyStore.load(is, passwordChars);
            context.logger.debug("key store key count for {}: {}", trustStoreFile, keyStore.size());
            return keyStore;
        } catch (Exception e) {
            context.logger.error("key store init failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
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
    
    public static Charset parseContentTypeCharset(String mimeType) {
        Map<String, String> map = parseContentTypeParams(mimeType);
        if (map == null) {
            return null;
        }
        String cs = map.get(CHARSET);
        if (cs == null) {
            return null;
        }
        return Charset.forName(cs);
    }
    
    public static Map<String, String> parseContentTypeParams(String mimeType) {
        List<String> items = StringUtils.split(mimeType, ';');
        int count = items.size();
        if (count <= 1) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap(count - 1);
        for (int i = 1; i < count; i++) {
            String item = items.get(i);
            int pos = item.indexOf('=');
            if (pos == -1) {
                continue;
            }
            String key = item.substring(0, pos).trim();
            String val = item.substring(pos + 1).trim();
            map.put(key, val);
        }
        return map;
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
        int pos = rawUri.indexOf('/');
        if (pos == -1) {
            return StringUtils.pair(null, "");
        }
        URI uri;
        try {
            uri = new URI(rawUri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (uri.getHost() == null) {
            return StringUtils.pair(null, rawUri);
        }
        String path = uri.getRawPath();
        pos = rawUri.indexOf(path);
        String urlBase = rawUri.substring(0, pos);
        return StringUtils.pair(urlBase, rawUri.substring(pos));
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
