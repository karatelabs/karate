Feature: JWT Test

Background:
* url 'http://klingman.us/api/jwtTest'
* def parseJwtPayload =
  """
  function(token) {
      var base64Url = token.split('.')[1];
      var base64Str = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      var Base64 = Java.type('java.util.Base64');
      var decoded = Base64.getDecoder().decode(base64Str);
      var String = Java.type('java.lang.String')
      return new String(decoded)
  };
  """

Scenario: jwt flow
Given path  'authenticate'
And request {user: "test@example.com", password: "testPass"}
When method POST
Then status 200
* def accessToken = response['token']
* json result = parseJwtPayload(accessToken)
* match result == {user: "test@example.com", role: "editor", exp: "#number", iss: "klingman"}

* path 'resource'
* header Authorization = 'Bearer ' + accessToken
* method get
* status 200

Scenario: Access Denied
Given path  'authenticate'
And request {user: "test@example.com", password: "wrong"}
When method POST
Then status 403

