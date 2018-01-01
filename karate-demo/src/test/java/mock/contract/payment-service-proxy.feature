Feature: payment service proxy

Scenario: pathMatches('/pay')   
    # if arg to karate.proceed() is null, incoming url will be used as-is (http proxy)
    * eval karate.proceed(paymentServiceUrl)
