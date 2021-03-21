package com.intuit.karate.core.parallel;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.MockHandler;
import com.intuit.karate.http.HttpServer;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.stream.Stream;

/**
 *
 * @author pthomas3
 */
class ParallelTest {

    static final Logger logger = LoggerFactory.getLogger(ParallelTest.class);

    static HttpServer server;
    static HttpServer slowServer;

    @BeforeAll
    static void beforeAll() {
        MockHandler mock = new MockHandler(Feature.read("classpath:com/intuit/karate/core/parallel/mock.feature"));
        server = HttpServer.handler(mock).build();

        MockHandler slowMock = new MockHandler(Feature.read("classpath:com/intuit/karate/core/parallel/slow-mock.feature"));
        slowServer = HttpServer.handler(slowMock).build();
    }

    @Test
    void testParallel() {
        Results results = Runner.path("classpath:com/intuit/karate/core/parallel/parallel.feature")
                .configDir("classpath:com/intuit/karate/core/parallel")
                .systemProperty("server.port", server.getPort() + "")
                .parallel(3);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

    @Test
    void testParallelWithCallSingleFailure() {
        // karate.callSingle() in karate-config-csfail.js
        // result of callSingle() will fail
        // test if anything with with Graal JS engine ...
        Results results = Runner.path("classpath:com/intuit/karate/core/parallel")//.path("classpath:com/intuit/karate/core/parallel/parallel.feature")
                .configDir("classpath:com/intuit/karate/core/parallel")
                .karateEnv("csfail")
                .tags("~@ignore")
                .systemProperty("server.port", server.getPort() + "")
                .parallel(3);

        System.out.println();
        assertEquals(0, results.getScenariosPassed(), results.getErrorMessages());
        results.getErrors().forEach(errorMessage -> {
            assertFalse(errorMessage.contains("org.graalvm.polyglot.PolyglotException: Multi threaded access requested"));
        });
    }


    @ParameterizedTest
    @MethodSource("parallelCallSingleTestParams")
    void testParallelWithDifferentCallSingleScenarios(String karateConfigEnvFile, int scenariosPassed) {
        // karate.callSingle() in karate-config-csfail.js
        // result of callSingle() will fail
        // test if anything with with Graal JS engine ...
        Results results = Runner.path("classpath:com/intuit/karate/core/parallel/parallel.feature",
                "classpath:com/intuit/karate/core/parallel/parallel-2.feature",
                "classpath:com/intuit/karate/core/parallel/parallel-outline-1.feature",
                "classpath:com/intuit/karate/core/parallel/parallel-outline-2.feature")
                .configDir("classpath:com/intuit/karate/core/parallel")
                .karateEnv(karateConfigEnvFile)
                .tags("~@ignore")
                .systemProperty("server.port", server.getPort() + "")
                .systemProperty("slowServerPort", slowServer.getPort() + "")
                .parallel(3);

        // regardless, never get the evil Multi thread access exception from GraalVM!
        results.getErrors().forEach(errorMessage -> {
            assertFalse(errorMessage.contains("org.graalvm.polyglot.PolyglotException: Multi threaded access requested"));
        });

        // another ugly scary Graal exception - don't get it!
        results.getErrors().forEach(errorMessage -> {
            assertFalse(errorMessage.matches("org\\.graalvm\\.polyglot\\.PolyglotException: The value 'DynamicObject<JSFunction>@.*' cannot be passed from one context to another\\. The current context is.*and the argument value originates from context .*"));
        });

        assertEquals(scenariosPassed, results.getScenariosPassed());

    }

    private static Stream<Arguments> parallelCallSingleTestParams() {
        return Stream.of(
                Arguments.of("callsingle", 14),
                Arguments.of("callsingle-api-call", 14),
                Arguments.of("callsingle-slow-api-call", 14),
                Arguments.of("callsingle-reuse-variable-outside-scope", 0),
                Arguments.of("callsingle-reuse-other-feature-result", 14),
                Arguments.of("callsingle-reuse-other-feature-result-2", 14)

                //callsingle-reuse-multiple-features
        );
    }
}
