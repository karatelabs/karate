package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import com.intuit.karate.shell.Command;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;

import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
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

    public static Charset parseContentTypeCharset(String mimeType) {
        Map<String, String> map = parseContentTypeParams(mimeType);
        if (map == null) {
            return null;
        }
        String cs = map.get("charset");
        if (cs == null) {
            return null;
        }
        return Charset.forName(cs);
    }

    public static Map<String, String> parseContentTypeParams(String mimeType) {
        List<String> items = StringUtils.split(mimeType, ';', false);
        int count = items.size();
        if (count <= 1) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>(count - 1);
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

    public static Map<String, String> parseUriPattern(String pattern, String url) {
        int qpos = url.indexOf('?');
        if (qpos != -1) {
            url = url.substring(0, qpos);
        }
        List<String> leftList = StringUtils.split(pattern, '/', false);
        List<String> rightList = StringUtils.split(url, '/', false);
        int leftSize = leftList.size();
        int rightSize = rightList.size();
        if (rightSize != leftSize) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>(leftSize);
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

    public static String normaliseUriPath(String uri) {
        uri = uri.indexOf('?') == -1 ? uri : uri.substring(0, uri.indexOf('?'));
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        return uri;
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
        pos = rawUri.lastIndexOf(path); // edge case that path is just "/"
        String urlBase = rawUri.substring(0, pos);
        return StringUtils.pair(urlBase, rawUri.substring(pos));
    }

    //==========================================================================
    //
    public static void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    public static void createSelfSignedCertificate(File cert, File key) {
        try {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            FileUtils.copy(ssc.certificate(), cert);
            FileUtils.copy(ssc.privateKey(), key);
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }
    
    private static final String PROXY_ALIAS = "karate-proxy";
    private static final String KEYSTORE_PASSWORD = "karate-secret";
    private static final String KEYSTORE_FILENAME = PROXY_ALIAS + ".jks";    

    public static SSLContext getSslContext(File keyStoreFile) {
        keyStoreFile = initKeyStore(keyStoreFile);
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(keyStoreFile), KEYSTORE_PASSWORD.toCharArray());
            TrustManager[] trustManagers = new TrustManager[]{LenientTrustManager.INSTANCE};
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
            kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());
            KeyManager[] keyManagers = kmf.getKeyManagers();
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(keyManagers, trustManagers, null);
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static File initKeyStore(File keyStoreFile) {
        if (keyStoreFile == null) {
            keyStoreFile = new File(KEYSTORE_FILENAME);
        }
        if (keyStoreFile.exists()) {
            if (logger.isTraceEnabled()) {
                logger.trace("keystore file already exists: {}", keyStoreFile);
            }
            return keyStoreFile;
        }
        File parentFile = keyStoreFile.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }
        Command.exec(false, parentFile, "keytool", "-genkey", "-alias", PROXY_ALIAS, "-keysize",
                "4096", "-validity", "36500", "-keyalg", "RSA", "-dname",
                "CN=" + PROXY_ALIAS, "-keypass", KEYSTORE_PASSWORD, "-storepass",
                KEYSTORE_PASSWORD, "-keystore", keyStoreFile.getName());
        Command.exec(false, parentFile, "keytool", "-exportcert", "-alias", PROXY_ALIAS, "-keystore",
                keyStoreFile.getName(), "-storepass", KEYSTORE_PASSWORD, "-file", keyStoreFile.getName() + ".der");
        return keyStoreFile;
    }

    public static FullHttpResponse createResponse(int status, String body) {
        return createResponse(HttpResponseStatus.valueOf(status), body);
    }

    public static FullHttpResponse createResponse(HttpResponseStatus status, String body) {
        byte[] bytes = FileUtils.toBytes(body);
        ByteBuf bodyBuf = Unpooled.copiedBuffer(bytes);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, bodyBuf);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        return response;
    }

    public static FullHttpResponse transform(FullHttpResponse original, String body) {
        FullHttpResponse response = createResponse(original.status(), body);
        response.headers().set(original.headers());
        return response;
    }

    private static final HttpResponseStatus CONNECTION_ESTABLISHED = new HttpResponseStatus(200, "Connection established");

    public static FullHttpResponse connectionEstablished() {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, CONNECTION_ESTABLISHED);
    }

    public static void fixHeadersForProxy(HttpRequest request) {
        String adjustedUri = ProxyContext.removeHostColonPort(request.uri());
        request.setUri(adjustedUri);
        request.headers().remove(HttpHeaderNames.CONNECTION);
        // addViaHeader(request, PROXY_ALIAS);
    }

    public static void addViaHeader(HttpMessage msg, String alias) {
        StringBuilder sb = new StringBuilder();
        sb.append(msg.protocolVersion().majorVersion()).append('.');
        sb.append(msg.protocolVersion().minorVersion()).append(' ');
        sb.append(alias);
        List<String> list;
        if (msg.headers().contains(HttpHeaderNames.VIA)) {
            List<String> existing = msg.headers().getAll(HttpHeaderNames.VIA);
            list = new ArrayList<>(existing);
            list.add(sb.toString());
        } else {
            list = Collections.singletonList(sb.toString());
        }
        msg.headers().set(HttpHeaderNames.VIA, list);
    }

}
