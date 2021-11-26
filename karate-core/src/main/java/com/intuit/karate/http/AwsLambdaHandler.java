/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minidev.json.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class AwsLambdaHandler {

    protected static final Logger logger = LoggerFactory.getLogger(AwsLambdaHandler.class);

    private static final String REQUEST_CONTEXT = "requestContext";
    private static final String DOMAIN_NAME = "domainName";
    private static final String HTTP = "http";
    private static final String HTTPS_PREFIX = "https://";
    private static final String METHOD = "method";
    private static final String RAW_PATH = "rawPath";
    private static final String QUERY_STRING_PARAMETERS = "queryStringParameters";
    private static final String HEADERS = "headers";
    private static final String COOKIES = "cookies";
    private static final String BODY = "body";
    private static final String IS_BASE64_ENCODED = "isBase64Encoded";
    private static final String STATUS_CODE = "statusCode";

    private final ServerHandler handler;

    public AwsLambdaHandler(ServerHandler handler) {
        this.handler = handler;
    }

    public void handle(InputStream in, OutputStream out) throws IOException {
        Map<String, Object> req = (Map) JSONValue.parse(in);
        if (logger.isTraceEnabled()) {
            logger.trace("request: {}", req);
        }
        Map<String, Object> ctx = (Map) req.get(REQUEST_CONTEXT);
        String domainName = (String) ctx.get(DOMAIN_NAME);
        Map<String, Object> http = (Map) ctx.get(HTTP);
        String method = (String) http.get(METHOD);
        String path = (String) req.get(RAW_PATH);
        Map<String, Object> rawParams = (Map) req.get(QUERY_STRING_PARAMETERS);
        Map<String, Object> rawHeaders = (Map) req.get(HEADERS);
        List<String> rawCookies = (List) req.get(COOKIES);
        String body = (String) req.get(BODY);
        Boolean isBase64Encoded = (Boolean) req.get(IS_BASE64_ENCODED);
        Request request = new Request();
        request.setUrlBase(HTTPS_PREFIX + domainName);
        request.setMethod(method);
        request.setPath(path);
        if (rawParams != null) {
            rawParams.forEach((k, v) -> request.setParamCommaDelimited(k, (String) v));
        }
        if (rawHeaders != null) {
            rawHeaders.forEach((k, v) -> request.setHeaderCommaDelimited(k, (String) v));
        }
        if (rawCookies != null) {
            request.setCookiesRaw(rawCookies);
        }
        if (body != null) {
            if (isBase64Encoded) {
                request.setBody(Base64.getDecoder().decode(body));
            } else {
                request.setBody(FileUtils.toBytes(body));
            }
        }
        Response response = handler.handle(request);
        Map<String, Object> res = new HashMap(4);
        res.put(STATUS_CODE, response.getStatus());
        Map<String, List<String>> responseHeaders = response.getHeaders();
        if (responseHeaders != null) {
            Map<String, String> temp = new HashMap(responseHeaders.size());
            responseHeaders.forEach((k, v) -> temp.put(k, StringUtils.join(v, ",")));
            res.put(HEADERS, temp);
        }
        boolean isBinary = response.isBinary();
        res.put(IS_BASE64_ENCODED, isBinary);
        byte[] responseBody = response.getBody();
        if (responseBody == null) {
            body = null;
        } else if (isBinary) {
            body = Base64.getEncoder().encodeToString(responseBody);
        } else {
            body = FileUtils.toString(responseBody);
        }
        res.put(BODY, body);
        if (logger.isTraceEnabled()) {
            logger.trace("response: {}", res);
        }
        out.write(JsonUtils.toJsonBytes(res));
    }

}
