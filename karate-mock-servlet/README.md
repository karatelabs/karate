# Karate Mock Servlet

## Test any Java Servlet without a Container
That's right, you can test Spring MVC or Spring Boot controllers and even Jersey JAX-RS resource end-points without having to start Tomcat, JBoss, WebSphere, Glassfish or the like.

And you can re-use your traditional HTTP integration tests without changes - just switch your environment, and Karate can run tests and bypass HTTP on the wire.

This can be a huge time-saver as you don't have to spend time waiting for your app-server to start and stop. You also don't need to worry about having free ports on your local machine, there's no more fiddling with HTTPS and certificates, and if you do things right - you can achieve TDD and code-coverage for all your application layers, starting from the web-controllers.

So yes, you can test HTTP web-services with the same ease that you expect from traditional unit-tests. Especially for micro-services - when you combine this approach with Karate's data-driven and data-matching capabilities, you can lean towards having more integration tests without losing any of the benefits of unit-tests.

## Using
### Maven

```xml
    <dependency>
        <groupId>com.intuit.karate</groupId>
        <artifactId>karate-mock-servlet</artifactId>
        <version>${karate.version}</version>
        <scope>test</scope>
    </dependency> 
```

## Switching the HTTP Client
You can completely customize the HTTP client used by Karate by implementing the [`HttpClient`](../karate-core/src/main/java/com/intuit/karate/http/HttpClient.java) interface which happens to be very simple.

If you need to create a completely new `HttpClient` implementation from scratch, the [`MockHttpClient`](src/main/java/com/intuit/karate/mock/servlet/MockHttpClient.java) is a good reference. There are many possibilities here, you can add support for other HTTP clients besides Apache and Jersey, or mock a stack that is not based on Java servlets.

Karate defaults to the [`ApacheHttpClient`](../karate-core/src/main/java/com/intuit/karate/http/ApacheHttpClient.java), and to change this for a test-run, you can set the [`HttpClientFactory`](../karate-core/src/main/java/com/intuit/karate/http/HttpClientFactory.java) using the [`Runner`](../karate-core/src/main/java/com/intuit/karate/Runner.java) "builder" API.

## Mocking Your Servlet
Creating a `Servlet` and `ServletContext` instance is up to you and here is where you would manage configuration for your web-application. And then you can implement `HttpClientFactory` by using the `MockHttpClient` code provided by Karate.

Once you refer to the following examples, you should be able to get up and running for your project.
* Spring MVC Dispatcher Servlet
  * [Spring Boot 1](src/test/java/demo/MockSpringMvcServlet.java)
  * [Spring Boot 2](../examples/mock-servlet/src/test/java/payment/mock/servlet/MockSpringMvcServlet.java)
* [Jersey JAX-RS Resource example](src/test/java/mock/jersey/MockJerseyServlet.java)

Note that the first example above tests the whole of the [`karate-demo`](../karate-demo) Spring Boot application, and using a [parallel runner](src/test/java/demo/MockSpringMvcServletTest.java)

If you structure your tests propertly, your `*.feature` files can be re-used for mock as well as *real* integration tests.

## Limitations
Most teams would not run into these, but if you do, please [consider contributing](https://github.com/intuit/karate/projects/3#card-22529274) !

* File Upload is not supported.
* Other similar edge-cases (such as redirects) are not supported.

Teams typically use the mock-servlet for simple JSON / XML request-response use cases. If you find some file-upload or negative-test scenarios failing because of the above limitations, you can choose to [`tag`](https://github.com/intuit/karate#tags) those tests to run only in your end-to-end integration test environment.





