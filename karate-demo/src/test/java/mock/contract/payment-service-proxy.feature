Feature: payment service proxy (or api-gateway !)

Background:
* if (paymentServiceUrl && paymentServiceUrl.startsWith('https')) karate.configure('ssl', true)

Scenario: pathMatches('/payments') && methodIs('post')
    * karate.proceed(paymentServiceUrl)
    # example of adding delay via a post-processing hook
    * def responseDelay = 3000

Scenario: pathMatches('/payments')
    * karate.proceed(paymentServiceUrl)
    * def responseDelay = 200 + Math.random() * 400

Scenario: pathMatches('/payments/{id}') && methodIs('delete')
    * karate.proceed(paymentServiceUrl)

Scenario: pathMatches('/payments/{id}')    
    * karate.proceed(paymentServiceUrl)

# 'catch-all' rule
Scenario:  
    # if arg to karate.proceed() is null, incoming url will be used as-is (http proxy)
    * karate.proceed(paymentServiceUrl)
