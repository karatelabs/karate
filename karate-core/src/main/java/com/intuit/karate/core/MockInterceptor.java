package com.intuit.karate.core;

import com.intuit.karate.http.Request;
import com.intuit.karate.http.Response;

@FunctionalInterface
public interface MockInterceptor {

  void intercept(Request req, Response res, Scenario scenario);

}