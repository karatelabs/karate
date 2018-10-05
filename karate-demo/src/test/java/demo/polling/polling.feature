Feature: demo of how to poll until a certain condition is met
    using a javascript function

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
    * def result = call read('get.feature')
    * def current = result.response
    * print 'current: ' + current
    * def target = current.id + 5
    * call waitUntil target
