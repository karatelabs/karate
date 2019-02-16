@mock-servlet-todo
Feature: demo of how to poll until a certain condition is met

Background:
    # first we re-set the counter to avoid collisions with other tests
    Given url demoBaseUrl
    And path 'greeting', 'reset'
    When method get
    Then status 200
    And match response == { counter: 0 }

    # you may prefer to read the javascript from a file instead of having it in-line
    * def waitUntil = 
    """
    function(x) {
      while (true) {
        var result = karate.call('get.feature');
        var greeting = result.response;
        karate.log('poll response', greeting); //<
        if (greeting.id >= x) {
          karate.log('condition satisfied, exiting');
          return;
        }
        karate.log('sleeping');
        // uncomment / modify the sleep time as per your wish
        // java.lang.Thread.sleep(1000);
      }
    }
    """

Scenario: get greeting and keep polling until id is n + 5
    using javascript, and a second feature
    * def result = call read('get.feature')
    * def current = result.response
    * print 'current: ' + current
    * def target = current.id + 5
    * call waitUntil target

Scenario: using the karate retry syntax
    # if not configured, 'retry' defaults to
    # { count: 3, interval: 3000 } (milliseconds)
    * configure retry = { count: 5, interval: 0 }
    Given url demoBaseUrl
    And path 'greeting'
    And retry until responseStatus == 200 && response.id > 3
    When method get
