@ignore
Feature: jwt test

Background:
* url demoBaseUrl
* def parseJwtPayload =
  """
  function(token) {
      var base64Url = token.split('.')[1];
      var base64Str = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      var Base64 = Java.type('java.util.Base64');
      var decoded = Base64.getDecoder().decode(base64Str);
      var String = Java.type('java.lang.String');
      return new String(decoded);
  }
  """

Scenario: jwt flow
    Given path 'echo', 'jwt'
    And request { username: 'john', password: 'secret' }
    When method POST
    Then status 200
    And json accessToken = parseJwtPayload(response)
    And match accessToken == { user: 'test@example.com', role: 'editor', exp: '#number', iss: 'klingman' }

    Given path 'echo', 'jwt', 'resource'
    And header Authorization = 'Bearer ' + accessToken
    When method get
    Then status 200
    And match response == 'success'

Scenario: access denied
    Given path 'echo', 'jwt'
    And request { username: 'john', password: 'wrong' }
    When method POST
    Then status 403
