# Karate Netty
## API Test-Doubles Made `Simple.`
And [Consumer Driven Contracts](https://martinfowler.com/articles/consumerDrivenContracts.html) made easy.

### Capabilities
* Super-easy 'hard-coded' mocks ([example](src/test/java/com/intuit/karate/mock/_mock.feature))
* Stateful mocks that can fully simulate CRUD for a micro-service ([example](../karate-demo/src/test/java/mock/proxy/demo-mock.feature))
* Easy HTTP request matching by path, method, headers etc.
* Use the full power of JavaScript expressions for HTTP request matching
* SSL / HTTPS with built-in self-signed certificate
* Forward HTTP requests to other URL-s (URL re-writing)
* Usable as a standard HTTP proxy server - simplifying configuration set-up for consuming applications
* Start and stop mock servers in milliseconds
* Super-fast HTTP response times (~20ms) for typical in-memory CRUD / JsonPath (as long as you don't do I/O)
* Thread-safe - use concurrent consumers or async flows without fear
* Easy integration into Java / JUnit test-suites via API
* Server can dynamically choose free port
* Think of it as a scriptable 'API gateway' or 'AOP for web-services' - insert custom functions before / after an HTTP request is handled
* Just *one* file can script the above aspects, simplifying the mental-model you need to have for advanced scenarios such as [Consumer Driven Contracts](https://martinfowler.com/articles/consumerDrivenContracts.html)
* Easily integrate messaging or async flows using Java-interop if required
* Enables consumer or even UI dev teams to work in parallel as the provider service is being developed
* [Stand-alone executable JAR](#standalone-jar) (20 MB) which only requires a JRE to run, ideal for web-developers or anyone who needs to quickly experiment with services.
* Built-in [CORS](#configure-cors) support for the ease of web-dev teams using the mock service
* Option to use an existing certificate and private-key for server-side SSL - making it easier for UI dev / browser consumers in some situations
* Configure a 'global' response header routine, ideal for browser consumers to add headers common for *all* responses - yet dynamic if needed
* Provider service dev team can practice TDD using the mock + contract-test
* The mock + contract-test serves as the ultimate form of documentation of the 'contract' including payload / schema details.

> For the last point above - Karate will have [Spring REST Docs](https://projects.spring.io/spring-restdocs/) support built-in in the future, please [help contribute](https://github.com/intuit/karate/issues/25) to completing this if you can !

This documentation is work in progress while this project evolves. But here is an end-to-end demo that should provide sufficient detail for those interested.

## Using
Note that you can use this as a [stand-alone JAR executable](#standalone-jar) which means that you don't even need to compile Java or use an IDE. If you need to embed the mock-server into a JUnit test, you can easily do so.

### Maven
Note that this includes the [`karate-apache`](https://github.com/intuit/karate#maven) dependency for convenience.

```xml
<dependency>
    <groupId>com.intuit.karate</groupId>
    <artifactId>karate-netty</artifactId>
    <version>${karate.version}</version>
    <scope>test</scope>
</dependency>  
```

## Consumer-Provider Example

<img src="src/test/resources/karate-test-doubles.jpg" height="720px"/>

We use a simplified example of a Java 'consumer' which makes HTTP calls to a Payment Service (provider) where `GET`, `POST`, `PUT` and `DELETE` have been implemented. The 'provider' implements CRUD for the [`Payment.java`](../karate-demo/src/test/java/mock/contract/Payment.java) 'POJO', and the `POST` (or create) results in a message ([`Shipment.java`](../karate-demo/src/test/java/mock/contract/Shipment.java) as JSON) being placed on a queue, which the consumer is listening to.

[ActiveMQ](http://activemq.apache.org) is being used for the sake of mixing an asynchronous flow into this example, and with the help of some [simple](../karate-demo/src/test/java/mock/contract/QueueUtils.java) [utilities](../karate-demo/src/test/java/mock/contract/QueueConsumer.java), we are able to mix asynchronous messaging into a Karate test *as well as* the test-double.

| Key    | Source Code | Description |
| ------ | ----------- | ----------- |
C | [`Consumer.java`](../karate-demo/src/test/java/mock/contract/Consumer.java) | The 'consumer' or client application that consumes the demo 'Payment Service' and also listens to a queue
P | [`PaymentService.java`](../karate-demo/src/test/java/mock/contract/PaymentService.java) | The provider 'Payment Service'
1 | [`ConsumerIntegrationTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerIntegrationTest.java) | An end-to-end integration test of the consumer that needs the *real* provider to be up and running
KC | [`payment-service.feature`](../karate-demo/src/test/java/mock/contract/payment-service.feature) | A 'normal' Karate functional-test that tests the 'contract' of the Payment Service from the perspective of the consumer
2 | [`PaymentServiceContractTest.java`](../karate-demo/src/test/java/mock/contract/PaymentServiceContractTest.java) | JUnit runner for the above Karate 'contract' test, that depends on the *real* provider being up and running
KP | [`payment-service-mock.feature`](../karate-demo/src/test/java/mock/contract/payment-service-mock.feature) | A 'state-ful' mock (or stub) that *fully* implements the 'contract' ! Yes, *really*.
3 | [`PaymentServiceContractUsingMockTest.java`](../karate-demo/src/test/java/mock/contract/PaymentServiceContractUsingMockTest.java) | Uses the above 'stub' to run the Payment Service 'contract' test
4 | [`ConsumerUsingMockTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerUsingMockTest.java) | Uses the 'fake' Payment Service 'stub' to run an integration test for the *real* consumer
KX | [`payment-service-proxy.feature`](../karate-demo/src/test/java/mock/contract/payment-service-proxy.feature) | Karate can act as a proxy with 'gateway like' capabilities, you can choose to either stub a response or delegate to a remote provider, depending on the incoming request. Think of the 'X' as being able to *transform* the HTTP request and response payloads as they pass through (and before returning)
5a | [`ConsumerUsingProxyHttpTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerUsingProxyHttpTest.java) | Here Karate is set up to act as an HTTP proxy, the advantage is that the consumer can use the 'real' provider URL, which simplifies configuration, provided that you can configure the consumer to use an HTTP proxy (ideally in a non-invasive fashion)
5b | [`ConsumerUsingProxyRewriteTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerUsingProxyRewriteTest.java) | Karate acts as a URL 're-writing' proxy. Here the consumer 'knows' only about the proxy. In this mode (as well as the above 'HTTP proxy' mode which uses the *same* script file), you can choose to either stub a response - or even forward the incoming HTTP request onto any remote URL you choose.

> Karate mocking a Queue has not been implemented for the last two flows (5) but can easily be derived from the other examples. So in (5) the Consumer is using the *real* queue.

### Server-Side Karate
#### A perfect match !
It is worth calling out *why* Karate on the 'other side of the fence' (*handling* HTTP requests instead of *making* them) - turns out to be remarkably effective, yet simple.

* 'Native' support for expressing JSON and XML payloads
* [Embedded Expressions](https://github.com/intuit/karate#embedded-expressions) are perfect for those parts of the payload that need to be dynamic, and JS functions can be 'in-lined' into the JSON or XML
* Manipulate or even transform payloads
* Validate payloads if needed, using a [simpler alternative to JSON schema](https://twitter.com/KarateDSL/status/878984854012022784)
* Karate is *all* about making HTTP calls, giving you the flexibility to call 'downstream' services if needed
* In-memory JSON and JsonPath solves for ['state' and filtering](https://twitter.com/KarateDSL/status/946607931327266816) if needed
* Mix custom JavaScript (or even Java code) if needed - for complex logic
* Easily 'seed' data or switch environment / config on start
* Read initial 'state' from a JSON file if needed

If you think about it, all the above are *sufficient* to implement *any* micro-service. Karate's DSL syntax is *focused* on exactly these aspects, thus opening up interesting possibilities. It may be hard to believe that you can spin-up a 'usable' micro-service in minutes with Karate - but do try it and see !

# Standalone JAR
Karate-Netty is available as a single, executable JAR file, which includes even the [`karate-apache`](https://mvnrepository.com/artifact/com.intuit.karate/karate-apache) dependency. This is ideal for handing off to UI / web-dev teams for example, who don't want to mess around with a Java IDE. All you need is the [JRE](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (at least version 1.8.0_112 or greater).

## Quick Start
It will take you only 2 minutes to see Karate's mock-server capabilities in action !

> Tip: Rename the file to `karate.jar` to make the commands below easier to type !

* Download the latest version of the JAR file from [Bintray](https://dl.bintray.com/ptrthomas/karate/), and it will have the name: `karate-<version>.jar`
* Download this file: [`cats-mock.feature`](../karate-demo/src/test/java/mock/web/cats-mock.feature) (or copy the text) to a local file next to the above JAR file
* In the same directory, start the mock server with the command: `java -jar karate.jar -m cats-mock.feature -p 8080`
* To see how this is capable of backing an HTML front-end, download this file: [`cats.html`](../karate-demo/src/test/java/mock/web/cats.html). Open it in a browser and you will be able to `POST` data. Browse to [`http://localhost:8080/cats`](http://localhost:8080/cats) - to see the saved data (state).
* You can also run a "normal" Karate test using the stand-alone JAR. Download this file: [`cats-test.feature`](../karate-demo/src/test/java/mock/web/cats-test.feature) - and run the command (in a separate console / terminal): `java -jar karate.jar cats-test.feature`
* You will see HTML reports in the `target/cucumber-html-reports` directory

## Usage
### Mock Server
You can view the command line help with the `-h` option:
```
java -jar karate.jar -h
```

To start a mock server, the 2 mandatory arguments are the path of the feature file 'mock' `-m` and the port `-p`

```
java -jar karate.jar -m my-mock.feature -p 8080
```
#### SSL
For SSL, use the `-s` flag. If you don't provide a certificate and key (see next section), it will automatically create `cert.pem` and `key.pem` in the current working directory, and the next time you re-start the mock server - these will be re-used. This is convenient for web / UI developers because you then need to set the certificate 'exception' only once in the browser.

```
java -jar karate.jar -m my-mock.feature -p 8443 -s
```

If you have a custom certificate and private-key (in PEM format) you can specify them, perhaps because these are your actual certificates or because they are trusted within your organization:

```
java -jar karate.jar -m my-mock.feature -p 8443 -c my-cert.crt -k my-key.key
```

### Running Tests
Convenient to run standard [Karate](https://github.com/intuit/karate) tests on the command-line without needing to mess around with Java or the IDE ! Great for demos or exploratory testing. Even HTML reports are generated !

> Note that if you are depending on external Java libraries or custom code to be compiled, this won't work.

Feature files (or search paths) to be tested don't need command-line flags or options and can be just listed at the end of the command.

```
java -jar karate.jar my-test.feature
```

You can specify [Cucumber tags](https://github.com/intuit/karate#cucumber-tags) to include (or exclude) using the `-t` or `--tags`  option as follows:

```
java -jar karate.jar -t @smoke,~@ignore my-test.feature
```

If your test depends on the `karate.env` [environment 'switch'](https://github.com/intuit/karate#switching-the-environment), you can specify that using the `-e` (env) option:

```
java -jar karate.jar -e e2e my-test.feature
```

If [`karate-config.js`](https://github.com/intuit/karate#configuration) exists in the current working directory, it will be used. You can specify a full path by setting the system property `karate.config`. Note that this is an easy way to set a bunch of variables, just return a JSON with the keys and values you need.

```
java -jar -Dkarate.config=somedir/my-config.js karate.jar my-test.feature
```

And you can even set or over-ride variable values via the command line by using the `-a` (args) option:

```
java -jar karate.jar -a myKey1=myValue1 -a myKey2=myValue2 my-test.feature
```

If you provide a directory in which multiple feature files are present (even in sub-folders), they will be all run. You can even specify the number of threads to run in parallel using `-T` or `--threads` (not to be confused with `-t` for tags):

```
java -jar karate.jar -T 5 -t ~@ignore src/features
```

The output directory where the `karate.log` file, JUnit XML and Cucumber report JSON files would be output will default to `target` in the current working directory. The Cucumber HTML report would be found in a folder called `cucumber-html-reports` within this "output" folder. You can change the output folder using the `-o` or `--output` option:

```
java -jar karate.jar -T 5 -t ~@ignore -o /my/custom/dir src/features
```

### UI
The 'default' command actually brings up the [Karate UI](https://github.com/intuit/karate/wiki/Karate-UI). So you can 'double-click' on the JAR or use this on the command-line:
```
java -jar karate.jar
```

You can also open an existing Karate test in the UI via the command-line:
```
java -jar karate.jar -u my-test.feature
```

## Logging
A default [logback configuration file](https://logback.qos.ch/manual/configuration.html) (named [`logback-netty.xml`](src/main/resources/logback-netty.xml)) is present within the stand-alone JAR. If you need to customize logging, set the system property `logback.configurationFile` to point to your custom config:
```
java -jar -Dlogback.configurationFile=my-logback.xml karate.jar my-test.feature
```
Here is the 'out-of-the-box' default which you can customize. Note that the default creates a folder called `target` and within it, logs will be in `karate.log`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
 
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
  
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>target/karate.log</file>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>    
   
    <logger name="com.intuit.karate" level="DEBUG"/>
   
    <root level="warn">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </root>
  
</configuration>
```

# Server Life Cycle
Writing a mock can get complicated for real-life API interactions, and most other frameworks attempt to solve this using declarative approaches, such as expecting you to create a large, complicated JSON to model all requests and responses. You can think of Karate's approach as combining the best of both the worlds of declarative and imperative programming. Combined with the capability to maintain state in the form of JSON objects in memory, and Karate's native support for [Json-Path](https://github.com/intuit/karate#jsonpath-filters), XML and [`embedded expressions`](https://github.com/intuit/karate#embedded-expressions) - you have a very powerful toolkit at your disposal. And Karate's intelligent defaults keep things dead simple.

The Karate 'server' life-cycle is simple and has only 2 phases - the `Background` and `Scenario`. You can see that the existing [`Gherkin`](https://github.com/cucumber/cucumber/wiki/Gherkin) format has been 're-purposed' for HTTP request handling. This means that you get the benefit of IDE support and syntax coloring for your mocks.

Refer to this example: [`demo-mock.feature`](../karate-demo/src/test/java/mock/proxy/demo-mock.feature).

## `Background`
This is executed on start-up. You can read files and set-up common functions and 'global' state here. Note that unlike the life-cycle of ['normal' Karate](https://github.com/intuit/karate#script-structure), the `Background` is *not* executed before each `Scenario`.

## `Scenario`
A server-side `Feature` file can have multiple `Scenario` sections in it. Each Scenario is expected to have a JavaScript expression as the content of the `Scenario` description.

On each incoming HTTP request, the `Scenario` expressions are evaluated in order, starting from the first one within the `Feature`. If the expression evaluates to `true`, the body of the `Scenario` is evaluated and the HTTP response is returned.

> It is good practice to have the last `Scenario` in the file with an empty description, (which will evaluate to `true`) so that it can act as a 'catch-all' and log or throw an error / `404 Not Found` in response.

## Request Matching

# Request 

## `request`

## `requestUrlBase`

## `requestUri`

## `requestMethod`

## `requestHeaders`

## `requestParams`
A map-like' object of all query-string parameters and the values will always be an array. Use the built-in convenience function [`paramValue(name)`](#paramValue) which will return a single (string) value (instead of an array) if the size of the parameter-list for that name is 1, which is what you need most of the time.

## `pathMatches`

## `pathParams`

## `methodIs`

## `paramValue`

## `headerContains`

## `typeContains`

## `acceptContains`

## `bodyPath`
Refer to this example: [`server.feature`](src/test/java/com/intuit/karate/netty/server.feature).

# Response

## `responseStatus`

## `response`

## `responseHeaders`
### `configure responseHeaders`
### `configure cors`

## `afterScenario`
Use this to add an artificial delay instead of calling `Thread.sleep()` directly which will block all other threads. For example:

```cucumber
* def afterScenario = function(){ java.lang.Thread.sleep(3000); }
```

Refer to this example: [`payment-service-proxy.feature`](../karate-demo/src/test/java/mock/contract/payment-service-proxy.feature).

## `karate.abort()`
Stop evaluating any more steps in the `Scenario` and return the `response`. Useful when combined with [`eval`](https://github.com/intuit/karate#eval) and conditional checks in JavaScript.

Refer to this example: [`server.feature`](src/test/java/com/intuit/karate/netty/server.feature).

# Proxy Mode
Refer to this example: [`payment-service-proxy.feature`](../karate-demo/src/test/java/mock/contract/payment-service-proxy.feature).

## `karate.proceed()`
Refer to the above example. A twist here is that if the parameter is `null` Karate will use the host in the incoming HTTP request as the target URL - which is what you want when you run Karate as an HTTP proxy.

The parameter has to be a URL that starts with `http` or `https`.

> Karate cannot act as an HTTPS proxy yet (do consider contributing !). But most teams are able to configure the "consumer" application to use HTTP and if you set the target URL for e.g. like this: `karate.proceed('https://myhost.com:8080')` Karate will proxy the current request to the server.

This is great because you have control before and after the actual call and you can modify the request or response or introduce time-delays.

# Stopping
A simple HTTP `GET` to `/__admin/stop` is sufficient to stop a running server gracefully. So you don't need to resort to killing the process, which can lead to issues especially on Windows - such as the port not being released.

If you have started the server programmatically via Java, you can keep a reference to the `FeatureServer` instance and call the `stop()` method. Here is an example: [ConsumerUsingMockTest.java](../karate-demo/src/test/java/mock/contract/ConsumerUsingMockTest.java).

