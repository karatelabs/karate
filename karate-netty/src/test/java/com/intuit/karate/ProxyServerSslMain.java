package com.intuit.karate;

import com.intuit.karate.netty.NettyUtils;
import com.intuit.karate.netty.ProxyResponse;
import com.intuit.karate.netty.ProxyServer;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.File;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ProxyServerSslMain {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ProxyServerSslMain.class);

    private String html = FileUtils.toString(new File("src/test/java/com/intuit/karate/temp.html"));

    @Test
    public void testProxy() {
        ProxyServer server = new ProxyServer(8090,
                req -> {
                    if ("httpbin.org".equals(req.context.host)) {
                        FullHttpResponse response = NettyUtils.createResponse(HttpResponseStatus.OK, html);
                        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/html");
                        return new ProxyResponse(req.context, req.request, response);
                    }
                    return null;
                },
                res -> {
                    if ("corte.si".equals(res.context.host) && res.isHtml()) {
                        logger.debug("returning html: {}", html);
                        return NettyUtils.transform(res.response, html);
                    }
                    return null;
                });
        server.waitSync();
    }

}
