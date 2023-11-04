package com.intuit.karate.gatling.javaapi;


import java.util.Collections;
import java.util.function.BiFunction;

import com.intuit.karate.Runner;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.gatling.KarateProtocol;
import com.intuit.karate.gatling.MethodPause;
import com.intuit.karate.http.HttpRequest;

import io.gatling.core.protocol.Protocol;
import io.gatling.javaapi.core.ProtocolBuilder;
import io.gatling.javaapi.core.internal.Converters;
import scala.collection.immutable.Seq;
import scala.collection.immutable.Map;

public class KarateProtocolBuilder implements ProtocolBuilder {

    public BiFunction<HttpRequest, ScenarioRuntime, String> nameResolver;
    public Runner.Builder runner = new Runner.Builder();    

    private final Map<String, Seq<MethodPause>> uriPatterns;

    // Takes a JAVA Map (easier for testing) containaing SCALA MethodPauses (easier to read, save an extra Java MethodPause class and another conversion)
    public KarateProtocolBuilder(java.util.Map<String, Seq<MethodPause>> uriPatterns) {
        this.uriPatterns = Converters.toScalaMap(uriPatterns);
    }

    @Override
    public KarateProtocol protocol() {
        KarateProtocol protocol = new KarateProtocol(uriPatterns);
        if (nameResolver != null) {
            protocol.nameResolver_$eq((req, sr) -> nameResolver.apply(req, sr));                
        }
        protocol.runner_$eq(runner);
        return protocol;
    }


}
