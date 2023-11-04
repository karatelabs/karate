package com.intuit.karate.gatling.javaapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.intuit.karate.Runner;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.http.HttpRequest;

import io.gatling.javaapi.core.internal.Converters;

import com.intuit.karate.gatling.KarateProtocol;
import com.intuit.karate.gatling.MethodPause;


class KarateProtocolBuilderTest   {

    // Validates that the supplied nameResolver, runner and uriPatterns are taken into account.
    @Test
    void karateProtocol() throws Exception {
        KarateProtocolBuilder protocolBuilder = new KarateProtocolBuilder(Collections.singletonMap("foo", Converters.toScalaSeq(Collections.singletonList(new MethodPause("get", 110)))));

        protocolBuilder.nameResolver = (req, sr) -> "test name resolver";

        protocolBuilder.runner.karateEnv("test");

        KarateProtocol protocol = protocolBuilder.protocol();

        assertEquals("test name resolver", protocol.nameResolver().apply(null, null));

        Field envField = Runner.Builder.class.getDeclaredField("env");
        envField.setAccessible(true);
        String env = (String)envField.get(protocol.runner());

        assertEquals("test", env);

        assertEquals(110, protocol.pauseFor("foo", "get"));
    }

    @Test
    void uriPatterns() {

        KarateProtocol protocol = KarateDsl.karateProtocol(
            KarateDsl.uri("foo").nil(),
            KarateDsl.uri("bar").pauseFor("get", 110, "post", 220)  
        ).protocol();        

        assertEquals(110, protocol.pauseFor("bar", "get"));
        assertEquals(220, protocol.pauseFor("bar", "post"));
        assertEquals(0, protocol.pauseFor("bar", "put"));
        assertEquals(0, protocol.pauseFor("foo", "get"));
        assertEquals(0, protocol.pauseFor("foobar", "get"));

        assertTrue(protocol.pathMatches("/foo").isDefined());
        assertTrue(protocol.pathMatches("/bar").isDefined());
        assertFalse(protocol.pathMatches("/foobar").isDefined());

    }
}
