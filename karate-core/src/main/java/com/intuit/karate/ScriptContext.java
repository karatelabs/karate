package com.intuit.karate;

import com.intuit.karate.validator.Validator;
import java.util.Map;
import java.util.logging.Level;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ScriptContext {

    private static final Logger logger = LoggerFactory.getLogger(ScriptContext.class);

    private static final String KARATE_NAME = "karate";
    private static final String VAR_CONTEXT = "_context";
    private static final String VAR_READ = "read";

    protected final ScriptValueMap vars;

    protected Client client;
    protected final Map<String, Validator> validators;
    protected final ScriptEnv env;

    // stateful config
    protected ScriptValue headers = ScriptValue.NULL;
    private ScriptValue readFunction;
    private boolean sslEnabled = false;
    private String sslAlgorithm = "TLS";
    private int readTimeout = -1;
    private int connectTimeout = -1;
    private String proxyUri;
    private String proxyUsername;
    private String proxyPassword;

    // needed for 3rd party code
    public ScriptValueMap getVars() {
        return vars;
    }

    public ScriptContext(ScriptEnv env, ScriptContext parent, Map<String, Object> arg) {
        this.env = env.refresh();
        if (parent != null) {
            vars = Script.clone(parent.vars);
            readFunction = parent.readFunction;
            validators = parent.validators;
            headers = parent.headers;
            sslEnabled = parent.sslEnabled;
            sslAlgorithm = parent.sslAlgorithm;
            readTimeout = parent.readTimeout;
            connectTimeout = parent.connectTimeout;
            proxyUri = parent.proxyUri;
            proxyUsername = parent.proxyUsername;
            proxyPassword = parent.proxyPassword;
            if (arg != null) {
                for (Map.Entry<String, Object> entry : arg.entrySet()) {
                    vars.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            vars = new ScriptValueMap();
            validators = Script.getDefaultValidators();
            readFunction = Script.eval(getFileReaderFunction(), this);
            try {
                Script.callAndUpdateVars("read('classpath:karate-config.js')", null, this);
            } catch (Exception e) {
                logger.warn("start-up configuration failed, missing or bad 'karate-config.js' - {}", e.getMessage());
            }
        }
        if (env.test) {
            logger.trace("karate init in test mode, http client disabled");
            client = null;
            return;
        }
        logger.trace("karate context init - initial properties: {}", vars);
        buildClient();
    }
    
    private static String getFileReaderFunction() {
        return "function(path) {\n"
                + "  var FileUtils = Java.type('" + FileUtils.class.getCanonicalName() + "');\n"
                + "  return FileUtils.readFile(path, " + VAR_CONTEXT + ").value;\n"
                + "}";
    }     

    public void configure(String key, String exp) { // TODO use enum
        key = StringUtils.trimToEmpty(key);
        ScriptValue value = Script.eval(exp, this);
        if (key.equals("headers")) {
            headers = value;
        } else if (key.equals("ssl")) {
            if (value.isString()) {
                sslEnabled = true;
                sslAlgorithm = value.getAsString();
            } else {
                sslEnabled = value.isBooleanTrue();
            }
            buildClient();
        } else if (key.equals("connectTimeout")) {
            connectTimeout = Integer.valueOf(value.getAsString());
            client.property(ClientProperties.CONNECT_TIMEOUT, connectTimeout);
            // lightweight operation, no need to re-build client
        } else if (key.equals("readTimeout")) {
            readTimeout = Integer.valueOf(value.getAsString());
            client.property(ClientProperties.READ_TIMEOUT, readTimeout);
            // lightweight operation, no need to re-build client
        } else if (key.equals("proxy")) {
            if (value.isString()) {
                proxyUri = value.getAsString();
            } else {
                Map<String, Object> map = (Map) value.getAfterConvertingToMapIfNeeded();
                proxyUri = (String) map.get("uri");
                proxyUsername = (String) map.get("username");
                proxyPassword = (String) map.get("password");
            }
            buildClient();
        } else {
            throw new RuntimeException("unexpected 'configure' key: '" + key + "'");
        }
    }

    public void buildClient() {
        ClientBuilder clientBuilder = ClientBuilder.newBuilder().register(MultiPartFeature.class);
        if (logger.isDebugEnabled()) {
            clientBuilder.register(new LoggingFeature(
                    java.util.logging.Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
                    Level.SEVERE,
                    LoggingFeature.Verbosity.PAYLOAD_TEXT, null));
        }
        clientBuilder.register(new RequestFilter(this));
        if (sslEnabled) {
            logger.info("ssl enabled, initializing generic trusted certificate / key-store with algorithm: {}", sslAlgorithm);
            SSLContext ssl = SslUtils.getSslContext(sslAlgorithm);
            HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
            clientBuilder.sslContext(ssl);
            clientBuilder.hostnameVerifier((host, session) -> true);
        }
        client = clientBuilder.build();
        if (connectTimeout != -1) {
            client.property(ClientProperties.CONNECT_TIMEOUT, connectTimeout);
        }
        if (readTimeout != -1) {
            client.property(ClientProperties.READ_TIMEOUT, readTimeout);
        }
        if (proxyUri != null) {
            client.property(ClientProperties.PROXY_URI, proxyUri);
        }
        if (proxyUsername != null) {
            client.property(ClientProperties.PROXY_USERNAME, proxyUsername);
        }
        if (proxyPassword != null) {
            client.property(ClientProperties.PROXY_PASSWORD, proxyPassword);
        }
    }
    
    public Map<String, Object> getVariableBindings() {
        Map<String, Object> map = Script.simplify(vars);
        if (readFunction != null) {
            map.put(VAR_READ, readFunction.getValue());
        }
        // for future function calls if needed, see getFileReaderFunction()
        map.put(VAR_CONTEXT, this);
        map.put(KARATE_NAME, new ScriptBridge(this));
        return map;
    }

}
