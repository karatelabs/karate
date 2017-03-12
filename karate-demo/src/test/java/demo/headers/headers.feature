Feature: greeting end-point

Background:
* configure headers = read('classpath:headers.js')
* url demoBaseUrl

Scenario: get auth token and cookie

    Given path 'headers'
    When method get
    Then status 200
    # the next two variables are used by 'headers.js'
    And def token = response
    And def time = cookies['time']

    # there is a cookie sent automatically
    # and an 'Authorization' header set by 'headers.js'
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

