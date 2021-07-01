Feature:

  Scenario: verify json key ordering retained from response

    #get payload & verify key ordering
    * string originalString = '{"echo":"echo@gmail.com","lambda":"Lambda","bravo":"1980-01-01"}'
    * json payload = originalString

    #create mock and do call
    * def port = karate.start('json-order-mock.feature').port
    * def simpleUrl = 'http://localhost:' + port + '/json_order'

    Given url simpleUrl
    And request payload
    When method POST
    Then status 200

    #verify response json key ordering
    * string responseString = response
    * match responseString == '{"tango":"Alice","foxtrot":"0.0.0.0","sierra":"Bob"}'

    #verify request json key ordering
    * string payloadString = payload
    * match payloadString == originalString