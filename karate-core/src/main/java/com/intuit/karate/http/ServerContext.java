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

import com.intuit.karate.resource.ResourceResolver;
import com.intuit.karate.FileUtils;
import com.intuit.karate.graal.JsArray;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.graal.Methods;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ServerContext implements ProxyObject {

    private static final Logger logger = LoggerFactory.getLogger(ServerContext.class);

    private static final String READ = "read";
    private static final String UUID = "uuid";
    private static final String REMOVE = "remove";
    private static final String SWITCH = "switch";
    private static final String AJAX = "ajax";
    private static final String HTTP = "http";
    private static final String TRIGGER = "trigger";
    private static final String AFTER_SETTLE = "afterSettle";
    private static final String TO_JSON = "toJson";
    private static final String FROM_JSON = "fromJson";

    private static final String[] KEYS = new String[]{READ, UUID, REMOVE, SWITCH, AJAX, HTTP, TRIGGER, AFTER_SETTLE, TO_JSON, FROM_JSON};
    private static final Set<String> KEY_SET = new HashSet(Arrays.asList(KEYS));
    private static final JsArray KEY_ARRAY = new JsArray(KEYS);

    private final ServerConfig config;
    private final Request request;

    private boolean stateless;
    private boolean api;
    private boolean lockNeeded;
    private Session session;

    private List<Map<String, Object>> responseTriggers;
    private List<String> afterSettleScripts;

    public ServerContext(ServerConfig config, Request request) {
        this.config = config;
        this.request = request;
        HTTP_FUNCTION = args -> {
            HttpClient client = config.getHttpClientFactory().apply(request);
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            if (args.length > 0) {
                http.url((String) args[0]);
            }
            return http;
        };
    }

    private static final String DOT_JS = ".js";

    public void prepare() {
        String path = request.getPath();
        if (request.getResourceType() == null) {
            request.setResourceType(ResourceType.fromFileExtension(path));
        }
        String resourcePath = request.getResourcePath();
        if (resourcePath == null) {
            if (api) {
                String pathParam = null;
                String jsPath = path + DOT_JS;
                resourcePath = jsPath;
                if (!config.getJsFiles().contains(jsPath)) {
                    List<String> pathParams = new ArrayList();
                    request.setPathParams(pathParams);
                    String temp = path;
                    do {
                        int pos = temp.lastIndexOf('/');
                        if (pos == -1) {
                            logger.debug("failed to extract path params: {} - {}", temp, this);
                            break;
                        }
                        String pp = temp.substring(pos + 1);
                        if (pathParams.isEmpty()) {
                            pathParam = pp;
                        }
                        pathParams.add(pp);
                        jsPath = temp.substring(0, pos) + DOT_JS;
                        temp = temp.substring(0, pos);
                    } while (!config.getJsFiles().contains(jsPath));
                    resourcePath = jsPath;
                }
                request.setPathParam(pathParam);
            } else { // static, note that HTML is resolved differently, by template resolver
                resourcePath = path;
            }
            request.setResourcePath(resourcePath);
        }
    }

    public String getSessionCookieValue() {
        List<String> values = request.getHeaderValues(HttpConstants.HDR_COOKIE);
        if (values == null) {
            return null;
        }
        for (String value : values) {
            Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(value);
            for (Cookie c : cookies) {
                if (config.getSessionCookieName().equals(c.name())) {
                    return c.value();
                }
            }
        }
        return null;
    }

    public Object read(String resource) {
        InputStream is = config.getResourceResolver().resolve(resource).getStream();
        String raw = FileUtils.toString(is);
        ResourceType resourceType = ResourceType.fromFileExtension(resource);
        if (resourceType == null) {
            return raw;
        }
        switch (resourceType) {
            case JS:
            case JSON:
                return RequestCycle.get().getLocalEngine().evalForValue("(" + raw + ")");
            default:
                return raw;
        }
    }

    public String toJson(Value value) {
        return new JsValue(value).toJson();
    }

    public ServerConfig getConfig() {
        return config;
    }

    public Request getRequest() {
        return request;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public boolean isLockNeeded() {
        return lockNeeded;
    }

    public void setLockNeeded(boolean lockNeeded) {
        this.lockNeeded = lockNeeded;
    }

    public boolean isStateless() {
        return stateless;
    }

    public void setStateless(boolean stateless) {
        this.stateless = stateless;
    }

    public boolean isAjax() {
        return request.isAjax();
    }

    public boolean isApi() {
        return api;
    }

    public void setApi(boolean api) {
        this.api = api;
    }

    public List<String> getAfterSettleScripts() {
        return afterSettleScripts;
    }

    public List<Map<String, Object>> getResponseTriggers() {
        return responseTriggers;
    }

    public void trigger(Map<String, Object> trigger) {
        if (responseTriggers == null) {
            responseTriggers = new ArrayList();
        }
        responseTriggers.add(trigger);
    }

    public void afterSettle(String js) {
        if (afterSettleScripts == null) {
            afterSettleScripts = new ArrayList();
        }
        afterSettleScripts.add(js);
    }
    
    private static final Supplier<String> UUID_FUNCTION = () -> java.util.UUID.randomUUID().toString();
    private final Function<String, Object> FROM_JSON_FUNCTION = s -> JsValue.fromString(s, false, null);
    private final Methods.FunVar HTTP_FUNCTION; // set in constructor
    
    private final Consumer<String> SWITCH_FUNCTION = s -> {
        RequestCycle.get().setSwitchTemplate(s);
        throw new RedirectException(s);
    };    

    private static final BiFunction<Object, Object, Object> REMOVE_FUNCTION = (o, k) -> {
        if (o instanceof Map && k != null) {
            Map in = (Map) o;
            Map out = new HashMap(in);
            Object removed = out.remove(k.toString());
            if (removed == null) {
                logger.warn("nothing removed, key not present: {}", k);
                return o;
            } else {
                return JsValue.fromJava(out);
            }
        } else if (o != null) {
            logger.warn("unable to cast to map: {} - {}", o.getClass(), o);
        }
        return o;
    };

    @Override
    public Object getMember(String key) {
        switch (key) {
            case READ:
                return (Function<String, Object>) this::read;
            case UUID:
                return UUID_FUNCTION;
            case TO_JSON:
                return (Function<Value, String>) this::toJson;
            case FROM_JSON:
                return FROM_JSON_FUNCTION;
            case REMOVE:
                return REMOVE_FUNCTION;
            case SWITCH:
                return SWITCH_FUNCTION;
            case AJAX:
                return isAjax();
            case HTTP:
                return HTTP_FUNCTION;
            case TRIGGER:
                return (Consumer<Map<String, Object>>) this::trigger;
            case AFTER_SETTLE:
                return (Consumer<String>) this::afterSettle;
            default:
                logger.warn("no such property on context object: {}", key);
                return null;
        }
    }

    @Override
    public Object getMemberKeys() {
        return KEY_ARRAY;
    }

    @Override
    public boolean hasMember(String key) {
        return KEY_SET.contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        logger.warn("put not supported on context object: {} - {}", key, value);
    }

}
