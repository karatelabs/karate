Feature: disable redirect handling

  Scenario:
    As a test writer,
    I want to prove the framework doesn't follow 302 redirect
    So that I can capture intermediate results.

    Given url 'https://goo.gl/W5xrS8'
    When method get
    Then status 301
    And match header Location == 'https://highaltitudearchery.com/'