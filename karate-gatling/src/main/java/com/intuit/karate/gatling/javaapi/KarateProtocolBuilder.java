/*
 * The MIT License
 *
 * Copyright 2023 Karate Labs Inc.
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

    // Takes a JAVA Map (easier for testing) containaing SCALA MethodPauses (easier to read, saves an extra Java MethodPause class and another conversion)
    public KarateProtocolBuilder(java.util.Map<String, Seq<MethodPause>> uriPatterns) {
        this.uriPatterns = Converters.toScalaMap(uriPatterns);
    }

    public KarateProtocolBuilder nameResolver(BiFunction<HttpRequest, ScenarioRuntime, String> nameResolver) {
        this.nameResolver = nameResolver;
        return this;
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
