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

import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.core.internal.Converters;
import io.gatling.javaapi.http.*;
import scala.collection.mutable.ArrayBuffer;
import scala.collection.mutable.Buffer;
import scala.collection.immutable.Seq;

import com.intuit.karate.gatling.PreDef;
import com.intuit.karate.gatling.javaapi.KarateUriPattern;
import com.intuit.karate.gatling.javaapi.KarateUriPattern.KarateUriPatternBuilder;
import com.intuit.karate.gatling.MethodPause;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class KarateDsl {

  public static KarateUriPatternBuilder uri(String uri) {
    return new KarateUriPatternBuilder(uri);
  }
  
  public static KarateProtocolBuilder karateProtocol(KarateUriPattern... patterns) {
    return new KarateProtocolBuilder(Arrays.stream(patterns).collect(Collectors.toMap(KarateUriPattern::getUri, pattern -> Converters.toScalaSeq(pattern.getPauses()))));
  }
  
    public static ActionBuilder karateFeature(String name, String... tags) {
       return new KarateFeatureBuilder(name, tags);
    }


    public static ActionBuilder karateSet(String key, final Function<Session, Object> supplier) {
       return () -> PreDef.karateSet(key, session -> supplier.apply(new Session(session)));
    }

    public static MethodPause method(String method, int durationInMillis) {
      return new MethodPause(method, durationInMillis);
    }
  
}
