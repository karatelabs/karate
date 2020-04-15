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
Karate actually allows you to switch the implementation of the Karate [`HttpClient`](../karate-core/src/main/java/com/intuit/karate/http/HttpClient.java) even *during* a test. For mocking a servlet container, you don't need to implement it from scratch and you just need to over-ride one or two methods of the mock-implementation that Karate provides.

> If you need to create a completely new `HttpClient` implementation from scratch, the [`MockHttpClient`](src/main/java/com/intuit/karate/mock/servlet/MockHttpClient.java) is a good reference. There are many possibilities here, you can add support for other HTTP clients besides Apache and Jersey, or mock a stack that is not based on Java servlets.

Let's take a closer look at the following [`configure`](https://github.com/intuit/karate#configure) keys:

 Key | Type | Description
------ | ---- | ---------
`httpClientClass` | string | The class name of the implementation you wish to use. By default the Karate Apache (or Jersey) HTTP client is used. You need a zero-argument constructor. If you need more control over construction, refer to the next row.
`httpClientInstance` | Java Object | A fully constructed instance of an `HttpClient` implementation. Useful if you need more dynamic control over things like dependency-injection.
`userDefined` | JSON | You normally would not need this general-purpose extension mechanism where you can pass custom JSON data to the HTTP Client instance via the [`configure`](https://github.com/intuit/karate#configure) keyword. Refer to [this test](../karate-core/src/test/java/com/intuit/karate/http/HttpClientTest.java) for an idea of how you can use this for advanced needs, such as if you wanted to customize your HTTP Client implementation *during* a test.

## Mocking Your Servlet
You only need to over-ride two methods: 
* `Servlet getServlet(HttpRequestBuilder request)`
* `ServletContext getServletContext()`

Once you refer to the following examples, you should be able to get up and running for your project.
* Spring MVC Dispatcher Servlet
  * [Spring Boot 1](src/test/java/demo/MockSpringMvcServlet.java)
  * [Spring Boot 2](../examples/mock-servlet/src/test/java/payment/mock/servlet/MockSpringMvcServlet.java)
* [Jersey JAX-RS Resource example](src/test/java/mock/jersey/MockJerseyServlet.java)

Note that the first example above tests the whole of the [`karate-demo`](../karate-demo) Spring Boot application, and using a [parallel runner](src/test/java/demo/MockSpringMvcServletTest.java)

## Configuration
Everything is typically tied together in [bootstrap configuration](https://github.com/intuit/karate#configuration). If you do this right, your `*.feature` files can be re-used for mock as well as *real* integration tests.

Use the test configuration for this `karate-mock-servlet` project as a reference: [`karate-config.js`](src/test/java/karate-config.js)

## Limitations
Most teams would not run into these, but if you do, please [consider contributing](https://github.com/intuit/karate/projects/3#card-22529274) !

* File Upload is not supported.
* Other similar edge-cases (such as redirects) are not supported.

Teams typically use the mock-servlet for simple JSON / XML request-response use cases. If you find some file-upload or negative-test scenarios failing because of the above limitations, you can choose to [`tag`](https://github.com/intuit/karate#tags) those tests to run only in your end-to-end integration test environment.





