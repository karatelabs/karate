Feature: a called feature will not clobber the parent context
    if the variable assignment syntax is used

Background:
# the shape of the next line is important. if the line starts with call (or callonce)
# the called script will update the 'global' context here in this file.
# but since we assigned it to a variable here on the next line - it does not
# but we can still use the variable to get any results from the 'call' if required
* def setup = callonce read('common.feature')
* url demoBaseUrl

Scenario: fail with a 400 since the header was not set
    Given path 'headers', setup.token
    And param url = demoBaseUrl
    When method get
    # error bad request
    Then status 400

Scenario: fail with a 400 since the cookie was not set
    * configure headers = { Authorization: '#(setup.token + setup.time + demoBaseUrl)' }
    Given path 'headers', setup.token
    And param url = demoBaseUrl
    When method get
    Then status 400

Scenario: manually set header and cookie to pass
    * headers { Authorization: '#(setup.token + setup.time + demoBaseUrl)' }
    * cookie time = setup.time
    Given path 'headers', setup.token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: alternative way of setting headers and cookies 1
    * configure headers = { Authorization: '#(setup.token + setup.time + demoBaseUrl)' }
    * configure cookies = { time: '#(setup.time)' }
    Given path 'headers', setup.token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: alternative way of setting headers and cookies 2
    * header Authorization = (setup.token + setup.time + demoBaseUrl)
    * cookies { time: '#(setup.time)' }
    Given path 'headers', setup.token
    And param url = demoBaseUrl
    When method get
    Then status 200
