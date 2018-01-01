Feature: payment service proxy (or api-gateway !)

Background:
* def sleep = function(t){ java.lang.Thread.sleep(t) }

Scenario: pathMatches('/payments') && methodIs('post')
    * eval karate.proceed(paymentServiceUrl)
    # example of post-processing
    * print response
    # * eval sleep(3000)

Scenario: pathMatches('/payments')
    * eval karate.proceed(paymentServiceUrl)

Scenario: pathMatches('/payments/{id}') && methodIs('delete')
    * eval karate.proceed(paymentServiceUrl)

Scenario: pathMatches('/payments/{id}')    
    * eval karate.proceed(paymentServiceUrl)

# 'catch-all' rule
Scenario: true  
    # if arg to karate.proceed() is null, incoming url will be used as-is (http proxy)
    * eval karate.proceed(paymentServiceUrl)
