package com.intuit.karate.runtime;

import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import static com.intuit.karate.runtime.RuntimeUtils.runScenario;
import com.intuit.karate.server.HttpServer;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class KarateMockHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(KarateMockHandlerTest.class);

    String URL_STEP = "url 'http://localhost:8080'";
    MockHandler handler;
    FeatureBuilder mock;
    ScenarioRuntime runtime;

    FeatureBuilder background(String... lines) {
        mock = FeatureBuilder.background(lines);
        return mock;
    }

    Object get(String name) {
        return runtime.engine.vars.get(name).getValue();
    }

    ScenarioRuntime run(String... lines) {
        handler = new MockHandler(mock.build());
        MockClient client = new MockClient(handler);
        runtime = runScenario(e -> client, lines);
        return runtime;
    }

    private void matchVar(String name, Object expected) {
        match(get(name), expected);
    }

    private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    @Test
    void testSimpleGet() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = 'hello world'");
        run(
                URL_STEP,
                "path '/hello'",
                "method get"
        );
        matchVar("response", "hello world");
    }
    
    @Test
    void testParam() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestParams");
        run(
                URL_STEP,
                "param foo = 'bar'",
                "path '/hello'",
                "method get"
        );
        matchVar("response", "{ foo: ['bar'] }");
    }

    @Test
    void testParams() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestParams");
        run(
                URL_STEP,
                "params { foo: 'bar' }",
                "path '/hello'",
                "method get"
        );
        matchVar("response", "{ foo: ['bar'] }");
    }     

}
