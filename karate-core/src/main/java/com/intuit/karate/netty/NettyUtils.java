/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.netty;

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import com.intuit.karate.http.LenientTrustManager;
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
import java.security.KeyStore;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
public class NettyUtils {

    private static final Logger logger = LoggerFactory.getLogger(NettyUtils.class);

    public static final String PROXY_ALIAS = "karate-proxy";
    private static final String KEYSTORE_PASSWORD = "karate-secret";
    private static final String KEYSTORE_FILENAME = PROXY_ALIAS + ".jks";

    private NettyUtils() {
        // only static methods
    }

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

    public static final FullHttpResponse connectionEstablished() {
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
            list = new ArrayList(existing);
            list.add(sb.toString());
        } else {
            list = Collections.singletonList(sb.toString());
        }
        msg.headers().set(HttpHeaderNames.VIA, list);
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

}
