Feature: payment service proxy

Scenario: true  
    # if arg to karate.proceed() is null, incoming url will be used as-is (http proxy)
    * eval karate.proceed(paymentServiceUrl)
