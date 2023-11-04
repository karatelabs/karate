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
       return () -> PreDef.karateFeature(name, Converters.toScalaSeq(tags));
    }


    public static ActionBuilder karateSet(String key, final Function<Session, Object> supplier) {
       return () -> PreDef.karateSet(key, session -> supplier.apply(new Session(session)));
    }

    public static MethodPause method(String method, int durationInMillis) {
      return new MethodPause(method, durationInMillis);
    }
  
}
