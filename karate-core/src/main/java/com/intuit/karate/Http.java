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
package com.intuit.karate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Http {

    private final Match match;

    public class Response {

        public int status() {
            return match.get("responseStatus").asInt();
        }

        public Match body() {
            return match.get("response");
        }
        
        public Match jsonPath(String exp) {
            return body().jsonPath(exp);
        }

    }

    private Http(Match match) {
        this.match = match;
    }

    public void setLogger(Logger logger) {
        match.context.logger = logger;
    }

    public Http url(String url) {
        match.context.url(Match.quote(url));
        return this;
    }

    public Http path(String... paths) {
        List<String> list = new ArrayList(paths.length);
        for (String p : paths) {
            list.add(Match.quote(p));
        }
        match.context.path(list);
        return this;
    }

    private Response handleError() {
        Response res = new Response();
        int code = res.status();
        if (code >= 400) {
            match.context.logger.warn("http response code: {}, response: {}, request: {}",
                    code, res.body().asString(), match.context.getPrevRequest());
        }
        return res;
    }

    public Response get() {
        match.context.method("get");
        return handleError();
    }

    public Response post(String body) {
        return post(new Json(body));
    }

    public Response post(Map<String, Object> body) {
        return post(new Json(body));
    }

    public Response post(ScriptValue body) {
        match.context.request(body);
        match.context.method("post");
        return handleError();
    }

    public Response post(Json json) { // avoid extra eval
        return post(json.getValue());
    }

    public Response delete() {
        match.context.method("delete");
        return handleError();
    }

    public static Http forUrl(Logger logger, String url) {
        Http http = new Http(Match.withHttp(logger));
        return http.url(url);
    }

    public Match config(String key, String value) {
        return match.config(key, value);
    }

}
