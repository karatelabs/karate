# Karate Netty
## API Test-Doubles and Contract Tests Made `Simple.`

Thanks to the developers of [Netty](http://netty.io) for such an *awesome* framework on which this is based.

### Capabilities
* Super-easy 'hard-coded' mocks [example](src/test/java/com/intuit/karate/mock/_mock.feature)
* Stateful mocks that can fully simulate CRUD for a micro-service [example](https://twitter.com/KarateDSL/status/946607931327266816)
* Easy HTTP request matching by path, method, headers etc.
* Use the full power of JavaScript expressions for HTTP request matching
* Forward HTTP requests to other URL-s (URL re-writing)
* Proxy HTTP requests
* AOP style API 'interceptor' - insert custom functions before and after an HTTP request is handled
* Start and stop mock servers in milliseconds
* Server can dynamically choose free port
* Mock is powerful enough to perform filter / interception, proxying, URL re-writing - almost like a lightweight, scriptable 'API gateway'
* Just *one* file can do all the above, simplifying the mental-model you need to have for advanced scenarios such as [contract-testing](https://martinfowler.com/articles/consumerDrivenContracts.html)


This documentation is work in progress while this project evolves. But here is a demo that should provide sufficient detail for those interested.

| File | Flow | Description |
| ---- | ---- | ----------- |
[`Consumer.java`](../karate-demo/src/test/java/mock/contract/Consumer.java) | C (Consumer) | The 'consumer' or client application that consumes the demo 'Payment Service'
[`PaymentService.java`](../karate-demo/src/test/java/mock/contract/PaymentService.java) | P (Producer) | The Payment Service that implements CRUD for the [`Payment.java`](../karate-demo/src/test/java/mock/contract/Payment.java) 'POJO'
[`ConsumerIntegrationTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerIntegrationTest.java) | C --> P | An end-to-end integration test of the consumer that needs the real PaymentService up and running
[`payment-service.feature`](../karate-demo/src/test/java/mock/contract/payment-service.feature) <br/> [`PaymentServiceContractTest.java`](../karate-demo/src/test/java/mock/contract/PaymentServiceContractTest.java) | KC (Karate Client) | A 'normal' Karate functional-test that tests the 'contract' of the Payment Service (and does not depend on the consumer)
[`payment-service-mock.feature`](../karate-demo/src/test/java/mock/contract/payment-service-mock.feature) | KS (Karate Server) | A 'state-ful' mock (or stub) that *fully* implements the contract ! Yes, really.
[`PaymentServiceContractUsingMockTest.java`](../karate-demo/src/test/java/mock/contract/PaymentServiceContractUsingMockTest.java) | KC --> KS | Uses the above 'stub' to run the Payment Service 'contract test'
[`ConsumerUsingMockTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerUsingMockTest.java) | C --> KS | Uses the 'fake' Payment Service 'stub' to run an integration test for the *real* Consumer
[`payment-service-proxy.feature`](../karate-demo/src/test/java/mock/contract/payment-service-proxy.feature) <br/> [`ConsumerUsingProxyHttpTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerUsingProxyHttpTest.java) | C --> KS --> P | Karate acts as an HTTP proxy, the advantage is that the consumer can use the 'real' producer URL, which simplifies configuration
[`ConsumerUsingProxyRewriteTest.java`](../karate-demo/src/test/java/mock/contract/ConsumerUsingProxyHttpTest.java) | C --> KS --> P | Karate acts as a URL re-writing proxy, here the consumer 'knows' only about the proxy. In this mode (as well as the above 'HTTP proxy' mode which uses the same script file), you can choose to either stub a response - or even forward the incoming HTTP request onto any remote URL you choose.




