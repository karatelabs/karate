Feature: multiple header management approaches

Background:

Given url demoBaseUrl
And path 'headers'
When method get
Then status 200
And def token = response
And def time = cookies['time']
# the response cookie will be sent for all subsequent requests as well

Scenario: configure function

    * configure headers = read('classpath:headers.js')

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: configure json

    * configure headers = { Authorization: '#(token + time + demoBaseUrl)' }

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200


Scenario: set header

    * configure headers = null
    * header Authorization = token + time + demoBaseUrl

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200


