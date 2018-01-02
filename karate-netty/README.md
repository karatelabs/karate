# Karate Netty
## API Test-Doubles Made `Simple.`
### + Consumer Driven Contract Testing

Thanks to the developers of [Netty](http://netty.io) for such an *awesome* framework on which this is based.

### Capabilities
* Super-easy 'hard-coded' mocks ([example](src/test/java/com/intuit/karate/mock/_mock.feature))
* Stateful mocks that can fully simulate CRUD for a micro-service ([example](https://twitter.com/KarateDSL/status/946607931327266816))
* Easy HTTP request matching by path, method, headers etc.
* Use the full power of JavaScript expressions for HTTP request matching
* Forward HTTP requests to other URL-s (URL re-writing)
* Proxy HTTP requests
* AOP style API 'interceptor' model - insert custom functions before and after an HTTP request is handled
* Start and stop mock servers in milliseconds
* Easy integration into Java / JUnit test-suites via API
* Server can dynamically choose free port
* Mock is powerful enough to perform filter / interception, proxying, URL re-writing - almost like a lightweight, scriptable 'API gateway'
* Just *one* file can script the above aspects, simplifying the mental-model you need to have for advanced scenarios such as [contract-testing](https://martinfowler.com/articles/consumerDrivenContracts.html)
* Enables consumer or even UI dev teams to work in parallel as the provider service is being developed
* Provider service dev team can practice TDD using the mock + contract-test
* The mock + contract-test serves as the ultimate form of documentation of the 'contract' including payload / schema details.

> For the last point above - Karate will have [Spring REST Docs](https://projects.spring.io/spring-restdocs/) support built-in in the future, please [help contribute](https://github.com/intuit/karate/issues/25) to completing this if you can !

This documentation is work in progress while this project evolves. But here is an end-to-end demo that should provide sufficient detail for those interested.

## Consumer-Producer Example
```                              
                   |¯¯¯¯¯¯¯¯¯¯¯¯|
|¯¯¯¯¯¯¯¯¯¯|       |  Payment   |
| Consumer |------>|  Service   |
|__________|       | (Producer) |
                   |____________|
```
We use a simple example of a Java 'consumer' which makes HTTP calls to a 'Payment Service'. `GET`, `POST` and `DELETE` have been implemented in the service (producer).

| File | Flow | Description |
| ---- | ---- | ----------- |
[`Consumer.java`](../karate-demo/src/test/java/mock/contract/Consumer.java) | C (Consumer) | The 'consumer' or client application that consumes the demo 'Payment Service'
[`PaymentService.java`](../karate-demo/src/test/java/mock/contract/PaymentService.java) | P (Producer) | The Payment Service that implements CRUD for the [`Payment.java`](../karate-demo/src/test/java/mock/contract/Payment.java) 'POJO'
[`ConsumerIntegrationTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerIntegrationTest.java) | C->P | An end-to-end integration test of the consumer that needs the *real* producer to be up and running
[`payment-service.feature`](../karate-demo/src/test/java/mock/contract/payment-service.feature) | KC (Karate Consumer / Contract) | A 'normal' Karate functional-test that tests the 'contract' of the Payment Service from the perspective of the consumer
[`PaymentServiceContractTest.java`](../karate-demo/src/test/java/mock/contract/PaymentServiceContractTest.java) | KC->P | JUnit runner for the above Karate 'contract' test, that depends on the *real* producer being up and running
[`payment-service-mock.feature`](../karate-demo/src/test/java/mock/contract/payment-service-mock.feature) | KP (Karate Producer) | A 'state-ful' mock (or stub) that *fully* implements the 'contract' ! Yes, *really*.
[`PaymentServiceContractUsingMockTest.java`](../karate-demo/src/test/java/mock/contract/PaymentServiceContractUsingMockTest.java) | KC->KP | Uses the above 'stub' to run the Payment Service 'contract' test
[`ConsumerUsingMockTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerUsingMockTest.java) | C->KP | Uses the 'fake' Payment Service 'stub' to run an integration test for the *real* consumer
[`payment-service-proxy.feature`](../karate-demo/src/test/java/mock/contract/payment-service-proxy.feature) | KX (Karate ProXy) | Karate can act as a proxy with 'gateway like' capabilities, you can choose to either stub a response or delegate to a remote producer, depending on the incoming request. Think of the 'X' as being able to *transform* the HTTP request and response payloads as they pass through (and before returning)
[`ConsumerUsingProxyHttpTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerUsingProxyHttpTest.java) | C->KX->P | Here Karate is set up to act as an HTTP proxy, the advantage is that the consumer can use the 'real' producer URL, which simplifies configuration, provided that you can configure the consumer to use an HTTP proxy (ideally in a non-invasive fashion)
[`ConsumerUsingProxyRewriteTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerUsingProxyHttpTest.java) | C->KX->P | Karate acts as a URL 're-writing' proxy. Here the consumer 'knows' only about the proxy. In this mode (as well as the above 'HTTP proxy' mode which uses the *same* script file), you can choose to either stub a response - or even forward the incoming HTTP request onto any remote URL you choose.

### Server-Side Karate
#### A match made in heaven
It is worth calling out *why* Karate on the 'other side of the fence' (*handling* HTTP requests instead of *making* them) - turns out to be remarkably effective, yet simple.

* 'Native' support for expressing JSON and XML
* Manipulate or even transform payloads
* Validate payloads, using a [simpler alternative to JSON schema](https://twitter.com/KarateDSL/status/878984854012022784) if needed
* Karate is *all* about making HTTP calls, giving you the flexibility to call 'downstream' services if needed
* In-memory JSON and JsonPath solves for ['state' and filtering](https://twitter.com/KarateDSL/status/946607931327266816) if needed
* Mix custom JavaScript (or even Java code) if needed - for complex logic
* Easily 'seed' data or switch environment / config on start

If you think about it, all the above are *sufficient* to implement *any* micro-service. Karate's DSL syntax is *focused* on exactly these aspects, thus opening up interesting possibilities. It may be hard to believe that you can spin-up a 'usable' micro-service in minutes with Karate - but do try it and see !


