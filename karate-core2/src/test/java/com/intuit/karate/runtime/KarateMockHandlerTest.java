package com.intuit.karate.runtime;

import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import static com.intuit.karate.runtime.RuntimeUtils.runScenario;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Calendar;

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
    SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd-MMM-yy HH:mm:ss z");

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

    @Test
    void testCookie() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "cookie foo = 'bar'",
                "path '/hello'",
                "method get"
        );
        matchVar("response", "{ Cookie: ['foo=bar'] }");
    }

    @Test
    void testCookieIsRemovedIfExpired() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(java.util.Calendar.DATE, -1);
        String pastDate = sdf.format(calendar.getTime());
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "cookie foo = {value:'bar', expires: '" + pastDate + "'}",
                "path '/hello'",
                "method get"
        );
        matchVar("response", "{}");
    }

    @Test
    void testCookieIsRetainedIfNotExpired() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(java.util.Calendar.DATE, +1);
        String futureDate = sdf.format(calendar.getTime());
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "cookie foo = {value:'bar', expires: '" + futureDate + "'}",
                "path '/hello'",
                "method get"
        );
        matchVar("response", "{ Cookie: ['foo=bar'] }");
    }

    @Test
    void testCookieisRemovedIfManuallyExpired() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        run(
                URL_STEP,
                "cookie foo = {value:'bar', max-age:'0'}",
                "path '/hello'",
                "method get"
        );
        matchVar("response", "{}");
    }

}
