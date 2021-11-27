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
import com.intuit.karate.graal.JsArray;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.graal.Methods;
import com.intuit.karate.template.KarateEngineContext;
import com.intuit.karate.template.TemplateUtils;
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
    private static final String RESOLVER = "resolver";
    private static final String READ_AS_STRING = "readAsString";
    private static final String EVAL = "eval";
    private static final String EVAL_WITH = "evalWith";
    private static final String GET = "get";
    private static final String UUID = "uuid";
    private static final String REMOVE = "remove";
    private static final String SWITCH = "switch";
    private static final String SWITCHED = "switched";
    private static final String AJAX = "ajax";
    private static final String HTTP = "http";
    private static final String NEXT_ID = "nextId";
    private static final String SESSION_ID = "sessionId";
    private static final String EXPIRE = "expire";
    private static final String RENDER = "render";
    private static final String BODY_APPEND = "bodyAppend";
    private static final String TO_JSON = "toJson";
    private static final String TO_JSON_PRETTY = "toJsonPretty";
    private static final String FROM_JSON = "fromJson";

    private static final String[] KEYS = new String[]{
        READ, RESOLVER, READ_AS_STRING, EVAL, EVAL_WITH, GET, UUID, REMOVE, SWITCH, SWITCHED, AJAX, HTTP,
        NEXT_ID, SESSION_ID, EXPIRE, RENDER, BODY_APPEND, TO_JSON, TO_JSON_PRETTY, FROM_JSON};
    private static final Set<String> KEY_SET = new HashSet(Arrays.asList(KEYS));
    private static final JsArray KEY_ARRAY = new JsArray(KEYS);

    private final ServerConfig config;
    private final Request request;

    private boolean stateless;
    private boolean api;
    private boolean httpGetAllowed;
    private boolean lockNeeded;
    private boolean newSession;
    private Session session; // can be pre-resolved, else will be set by RequestCycle.init()
    private boolean switched;
    private Supplier<Response> customHandler;
    private int nextId;

    private final Map<String, Object> variables;
    private List<String> bodyAppends;

    public ServerContext(ServerConfig config, Request request) {
        this(config, request, null);
    }

    public ServerContext(ServerConfig config, Request request, Map<String, Object> variables) {
        this.config = config;
        this.request = request;
        this.variables = variables;
        HTTP_FUNCTION = args -> {
            HttpClient client = config.getHttpClientFactory().apply(request);
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            if (args.length > 0) {
                http.url((String) args[0]);
            }
            return http;
        };
        RENDER_FUNCTION = o -> {
            if (o instanceof String) {
                JsEngine je = RequestCycle.get().getEngine();
                return TemplateUtils.renderResourcePath((String) o, je, config.getResourceResolver());
            }
            Map<String, Object> map;
            if (o instanceof Map) {
                map = (Map) o;
            } else {
                logger.warn("invalid argument to render: {}", o);
                return null;
            }
            Map<String, Object> templateVars = (Map) map.get("variables");
            String path = (String) map.get("path");
            String html = (String) map.get("html");
            Boolean fork = (Boolean) map.get("fork");
            Object swap = map.get("swap");
            if (path == null && html == null) {
                logger.warn("invalid argument to render, 'path' or 'html' needed: {}", map);
                return null;
            }
            JsEngine je;
            if (fork != null && fork) {
                je = JsEngine.local();
            } else {
                je = RequestCycle.get().getEngine().copy();
            }
            if (templateVars != null) {
                je.putAll(templateVars);
            }
            String body;
            if (path != null) {
                body = TemplateUtils.renderResourcePath(path, je, config.getResourceResolver());
            } else {
                body = TemplateUtils.renderHtmlString(html, je, config.getResourceResolver());
            }
            if (swap != null) {
                String id = (String) map.get("id");
                StringBuilder sb = new StringBuilder();
                sb.append("<div");
                if (id != null) {
                    sb.append(" id=\"").append(id).append("\"");
                }
                sb.append(" hx-swap-oob=\"").append(swap).append("\">");
                sb.append(body);
                sb.append("</div>");
                String appended = sb.toString();
                bodyAppend(appended);
            }
            return body;
        };
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

    public String readAsString(String resource) {
        InputStream is = config.getResourceResolver().resolve(resource).getStream();
        return FileUtils.toString(is);
    }

    public Object read(String resource) {
        String raw = readAsString(resource);
        ResourceType resourceType = ResourceType.fromFileExtension(resource);
        if (resourceType == ResourceType.JS) {
            return eval(raw);
        } else {
            return JsValue.fromString(raw, false, resourceType);
        }
    }

    public Object eval(String source) {
        return RequestCycle.get().getEngine().evalForValue(source);
    }

    public Object evalWith(Object o, String source) {
        Value value = Value.asValue(o);
        return RequestCycle.get().getEngine().evalWith(value, source, true);
    }

    public String toJson(Object o) {
        Value value = Value.asValue(o);
        return new JsValue(value).toJsonOrXmlString(false);
    }

    public String toJsonPretty(Object o) {
        Value value = Value.asValue(o);
        return new JsValue(value).toJsonOrXmlString(true);
    }

    public ServerConfig getConfig() {
        return config;
    }

    public Request getRequest() {
        return request;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public boolean isNewSession() {
        return newSession;
    }

    public void setNewSession(boolean newSession) {
        this.newSession = newSession;
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

    public boolean isHttpGetAllowed() {
        return httpGetAllowed;
    }

    public void setHttpGetAllowed(boolean httpGetAllowed) {
        this.httpGetAllowed = httpGetAllowed;
    }

    public Supplier<Response> getCustomHandler() {
        return customHandler;
    }

    public void setCustomHandler(Supplier<Response> customHandler) {
        this.customHandler = customHandler;
    }

    public boolean isSwitched() {
        return switched;
    }

    public List<String> getBodyAppends() {
        return bodyAppends;
    }

    public void bodyAppend(String body) {
        if (bodyAppends == null) {
            bodyAppends = new ArrayList();
        }
        bodyAppends.add(body);
    }

    private final Methods.FunVar GET_FUNCTION = args -> {
        if (args.length == 0 || args[0] == null) {
            return null;
        }
        String name = args[0].toString();
        KarateEngineContext kec = KarateEngineContext.get();
        if (args.length == 1) {
            return kec.getVariable(name);
        }
        if (kec.containsVariable(name)) {
            return kec.getVariable(name);
        } else {
            return args[1];
        }
    };

    private static final Supplier<String> UUID_FUNCTION = () -> java.util.UUID.randomUUID().toString();
    private static final Function<String, Object> FROM_JSON_FUNCTION = s -> JsValue.fromString(s, false, null);

    private final Methods.FunVar HTTP_FUNCTION; // set in constructor
    private final Function<Object, String> RENDER_FUNCTION; // set in constructor    

    private final Methods.FunVar SWITCH_FUNCTION = args -> {
        if (switched) {
            logger.warn("context.switch() can be called only once during a request, ignoring: {}", args[0]);
        } else {
            switched = true; // flag for request cycle render
            KarateEngineContext.get().setRedirect(true); // flag for template engine
            RequestCycle rc = RequestCycle.get();
            if (args.length > 1) {
                Value value = Value.asValue(args[1]);
                if (value.hasMembers()) {
                    JsValue jv = new JsValue(value);
                    rc.setSwitchParams(jv.getAsMap());
                }
            }
            String template;
            if (args.length > 0) {
                template = args[0].toString();
                rc.setSwitchTemplate(template);
            } else {
                template = null;
            }
            throw new RedirectException(template);
        }
        return null;
    };

    private final Supplier<String> EXPIRE_FUNCTION = () -> {
        RequestCycle.get().setExpired(true);
        return null;
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

    private final Supplier<Integer> NEXT_ID_FUNCTION = () -> ++nextId;

    @Override
    public Object getMember(String key) {
        switch (key) {
            case READ:
                return (Function<String, Object>) this::read;
            case READ_AS_STRING:
                return (Function<String, String>) this::readAsString;
            case EVAL:
                return (Function<String, Object>) this::eval;
            case EVAL_WITH:
                return (BiFunction<Object, String, Object>) this::evalWith;
            case GET:
                return GET_FUNCTION;
            case UUID:
                return UUID_FUNCTION;
            case TO_JSON:
                return (Function<Object, String>) this::toJson;
            case TO_JSON_PRETTY:
                return (Function<Object, String>) this::toJsonPretty;
            case FROM_JSON:
                return FROM_JSON_FUNCTION;
            case REMOVE:
                return REMOVE_FUNCTION;
            case SWITCH:
                return SWITCH_FUNCTION;
            case SWITCHED:
                return switched;
            case AJAX:
                return isAjax();
            case HTTP:
                return HTTP_FUNCTION;
            case NEXT_ID:
                return NEXT_ID_FUNCTION;
            case SESSION_ID:
                return session == null ? null : session.getId();
            case EXPIRE:
                return EXPIRE_FUNCTION;
            case RENDER:
                return RENDER_FUNCTION;
            case BODY_APPEND:
                return (Consumer<String>) this::bodyAppend;
            case RESOLVER:
                return config.getResourceResolver();
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
