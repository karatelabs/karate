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

    private static final String HTTP_METHOD = "httpMethod";
    private static final String PATH = "path";
    private static final String MULTI_PARAMS = "multiValueQueryStringParameters";
    private static final String MULTI_HEADERS = "multiValueHeaders";
    private static final String BODY = "body";
    private static final String IS_BASE64_ENCODED = "isBase64Encoded";
    private static final String STATUS_CODE = "statusCode";

    private final ServerHandler handler;

    public AwsLambdaHandler(ServerHandler handler) {
        this.handler = handler;
    }

    public void handle(InputStream in, OutputStream out) throws IOException {
        Map<String, Object> req = (Map) JSONValue.parse(in);
        // logger.debug("request: {}", req);
        String method = (String) req.get(HTTP_METHOD);
        String path = (String) req.get(PATH);
        Map<String, List<String>> params = (Map) req.get(MULTI_PARAMS);
        Map<String, List<String>> headers = (Map) req.get(MULTI_HEADERS);
        String body = (String) req.get(BODY);
        Boolean isBase64Encoded = (Boolean) req.get(IS_BASE64_ENCODED);
        Request request = new Request();
        request.setMethod(method);
        request.setPath(path);
        request.setParams(params);
        request.setHeaders(headers);
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
        res.put(MULTI_HEADERS, response.getHeaders());
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
        out.write(JsonUtils.toJsonBytes(res));
    }

}
