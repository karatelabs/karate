package com.intuit.karate.core;

import static com.intuit.karate.TestUtils.*;
import static com.intuit.karate.TestUtils.runScenario;
import com.intuit.karate.http.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class KarateHttpMockHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(KarateHttpMockHandlerTest.class);

    MockHandler handler;
    HttpServer server;
    FeatureBuilder mock;
    ScenarioRuntime runtime;    
    
    String urlStep() {
        return "url 'http://localhost:" + server.getPort() + "'";
    }

    void startMockServer() {
        handler = new MockHandler(mock.build());
        server = new HttpServer(0, handler);       
    }

    FeatureBuilder background(String... lines) {
        mock = FeatureBuilder.background(lines);
        return mock;
    }

    Object get(String name) {
        return runtime.engine.vars.get(name).getValue();
    }

    ScenarioRuntime run(String... lines) {        
        runtime = runScenario(null, lines);
        return runtime;
    }

    private void matchVar(String name, Object expected) {
        match(get(name), expected);
    }
    
    private void matchVarContains(String name, Object expected) {
        matchContains(get(name), expected);
    }    

    @AfterEach
    void afterEach() {
        server.stop();
    }

    @Test
    void testSimpleGet() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = 'hello world'");
        startMockServer();
        run(
                urlStep(), 
                "path '/hello'",
                "method get"
        );
        matchVar("response", "hello world");
    }
    
    @Test
    void testThatCookieIsPartOfRequestForApache() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        startMockServer();
        run(
                urlStep(), 
                "path '/hello'",
                "cookie foo = 'bar'",
                "method get"
        );
        matchVarContains("response", "{ cookie: ['foo=bar'] }");        
        
    }

}
