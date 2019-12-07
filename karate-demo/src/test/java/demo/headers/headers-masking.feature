@apache
@mock-servlet-todo
Feature: how to mask headers or payload if needed, see Java code in demo.headers.DemoLogModifier

Background:
    # if this was in karate-config.js, it would apply "globally"
    * def LM = Java.type('demo.headers.DemoLogModifier')
    * configure logModifier = new LM()

    Given url demoBaseUrl
    And path 'headers'
    When method get
    Then status 200
    And def token = response
    And def time = responseCookies.time.value 

Scenario: set header
    * header Authorization = token + time + demoBaseUrl
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

