package com.intuit.karate.core;

import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import static com.intuit.karate.core.RuntimeUtils.runScenario;
import com.intuit.karate.http.HttpServer;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    
    private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
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

}
