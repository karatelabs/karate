# Karate Consumer Driven Contracts Demo

## References
This is a simplified version of the [example in the Karate test-doubles documentation](https://github.com/intuit/karate/tree/master/karate-netty#consumer-provider-example) - with JMS / queues removed and simplified to be a stand-alone maven project.

Also see [The World's Smallest Microservice](https://www.linkedin.com/pulse/worlds-smallest-micro-service-peter-thomas/).

## Instructions
* clone the project
* `mvn clean test`

## Main Artifacts
| File | Description | Comment |
| ---- | ----------- | ------- |
| [PaymentService.java](payment-producer/src/main/java/payment/producer/PaymentService.java) | Producer | A very simple [Spring Boot](https://spring.io/projects/spring-boot) app / REST service |
| [payment-contract.feature](payment-producer/src/test/java/payment/producer/contract/payment-contract.feature) | Contract + Functional Test | [Karate](https://github.com/intuit/karate) API test |
| [PaymentContractTest.java](payment-producer/src/test/java/payment/producer/contract/PaymentContractTest.java) | Producer Integration Test | JUnit runner for the above |
| [payment-mock.feature](payment-producer/src/test/java/payment/producer/mock/payment-mock.feature) | Mock / Stub | [Karate mock](https://github.com/intuit/karate/tree/master/karate-netty) that *perfectly* simulates the Producer ! | 
| [PaymentContractAgainstMockTest.java](payment-producer/src/test/java/payment/producer/mock/PaymentContractAgainstMockTest.java) | Verify that the Mock is as per Contract | JUnit runner that points `payment-contract.feature` --> `payment-mock.feature` |
| [Consumer.java](payment-consumer/src/main/java/payment/consumer/Consumer.java) | Consumer | A simple Java app that calls the Producer to do some work |
| [ConsumerIntegrationTest.java](payment-consumer/src/test/java/payment/consumer/ConsumerIntegrationTest.java) | Consumer Integration Test | A JUnit *full* integration test, using the *real* Consumer and Producer |
| [ConsumerIntegrationAgainstMockTest.java](payment-consumer/src/test/java/payment/consumer/ConsumerIntegrationAgainstMockTest.java) | Consumer Integration Test but using the Mock | Like the above but using the mock Producer |
