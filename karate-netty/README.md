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
* Option to use an existing certificate and private-key for server-side SSL - making it easier for UI dev / browser consumers in some situations
* Configure a 'global' response header routine, ideal for browser consumers to handle [CORS](https://spring.io/understanding/CORS) for e.g.
* Provider service dev team can practice TDD using the mock + contract-test
* The mock + contract-test serves as the ultimate form of documentation of the 'contract' including payload / schema details.

> For the last point above - Karate will have [Spring REST Docs](https://projects.spring.io/spring-restdocs/) support built-in in the future, please [help contribute](https://github.com/intuit/karate/issues/25) to completing this if you can !

This documentation is work in progress while this project evolves. But here is an end-to-end demo that should provide sufficient detail for those interested.

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
* Validate payloads, using a [simpler alternative to JSON schema](https://twitter.com/KarateDSL/status/878984854012022784) if needed
* Karate is *all* about making HTTP calls, giving you the flexibility to call 'downstream' services if needed
* In-memory JSON and JsonPath solves for ['state' and filtering](https://twitter.com/KarateDSL/status/946607931327266816) if needed
* Mix custom JavaScript (or even Java code) if needed - for complex logic
* Easily 'seed' data or switch environment / config on start
* Read initial 'state' from a JSON file if needed

If you think about it, all the above are *sufficient* to implement *any* micro-service. Karate's DSL syntax is *focused* on exactly these aspects, thus opening up interesting possibilities. It may be hard to believe that you can spin-up a 'usable' micro-service in minutes with Karate - but do try it and see !


