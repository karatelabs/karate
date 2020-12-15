Feature: modifying cookies

Background:
* url demoBaseUrl

Scenario: no cookies
    Given path 'search', 'cookies'
    When method get
    Then status 200
    And match response == []

Scenario: one cookie, and it is sent automatically in the next request
    Given path 'search', 'cookies'
    And cookie foo = 'bar'
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0] contains { name: 'foo', value: 'bar' }

    Given path 'search', 'cookies'
    And request {}
    When method post
    Then status 200
    And match response == '#[1]'
    And match response[0] contains { name: 'foo', value: 'bar' }

    * print 'cookies: ', responseCookies

    # reset cookies
    * configure cookies = null
    Given path 'search', 'cookies'
    When method get
    Then status 200
    And match response == []

    # modify cookies
    Given path 'search', 'cookies'
    And cookie foo = 'blah'
    And request {}
    When method post
    And match response == '#[1]'
    And match response[0] contains { name: 'foo', value: 'blah' }

Scenario: cookie as json
    Given path 'search', 'cookies'
    And cookie foo = { value: 'bar' }
    When method get
    Then status 200
    And match response[0] contains { name: 'foo', value: 'bar' }

Scenario: cookie returned has dots in the domain which violates RFC 2109
    Given path 'search', 'cookies'
    And cookie foo = { value: 'bar' }
    And param domain = '.abc.com'
    When method get
    Then status 200
    And match response[0] contains { name: 'foo', value: 'bar', domain: '.abc.com' }

Scenario: cookie returned has dots in the domain which violates RFC 2109
    Given path 'search', 'cookies'
    And cookie foo = { value: 'bar' }
    And param domain = '.abc.com'
    When method get
    Then status 200
    And match response[0] contains { name: 'foo', value: 'bar', domain: '.abc.com' }

@mock-servlet-todo
Scenario: non-expired cookie is in response
    * def futureDate =
    """
    function() {
      var SimpleDateFormat = Java.type('java.text.SimpleDateFormat');
      var Calendar = Java.type('java.util.Calendar');
      var currCalIns = Calendar.getInstance();
      currCalIns.add(java.util.Calendar.DATE, +1);
      var sdf = new SimpleDateFormat("EEE, dd-MMM-yy HH:mm:ss z");
      return sdf.format(currCalIns.getTime());
    }
    """
    * def date = futureDate()
    Given path 'search', 'cookies'
    And cookie foo = {value:'bar', expires:'#(date)', path:'/search'}
    And param domain = '.abc.com'
    When method get
    Then status 200
    And match response[0] contains { name: 'foo', value: 'bar', domain: '.abc.com' }

Scenario: max-age is -1, cookie should persist.
    Given path 'search', 'cookies'
    And cookie foo = {value:'bar', max-age:'-1', path:'/search'}
    And param domain = '.abc.com'
    When method get
    Then status 200
    And match response[0] contains { name: 'foo', value: 'bar', domain: '.abc.com' }