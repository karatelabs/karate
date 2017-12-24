Feature: multiple header management approaches that demonstrate how after
    an initial 'sign-in' that retrieves some secure tokens, every subsequent
    request can have the 'Authorization' header set in a way that the server expects

Background:
    
    # the call below performs the function of a sign-in
    # a string token is returned, which needs to be combined with a cookie and the url
    # to form the 'Authorization' header. calls to /headers/{token} will fail unless
    # the Authorization header is set correctly.

    Given url demoBaseUrl
    And path 'headers'
    When method get
    Then status 200
    And def token = response
    And def time = responseCookies.time.value

    # the above flow will typically need to be re-used by multiple features
    # refer to 'call-updates-config.feature' for the recommended approach

    # note that the responseCookies will be auto-sent as cookies for all future requests
    # even the responseCookies can be validated using 'match'
    And match responseCookies contains { time: '#notnull' }
    # example of how to check that a cookie does NOT exist
    And match responseCookies !contains { blah: '#notnull' }
   
Scenario: configure function
    this is the approach that most projects would use, especially if some header needs
    to be dynamic for each request. for e.g. see how a 'request_id' header is set in 'headers.js'
    for an example of how the steps in the 'Background:' can be moved into a re-usable feature
    refer to 'call-updates-config.feature' and 'common.feature'

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
    * header Authorization = token + time + demoBaseUrl
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: multi-value headers
    * header Authorization = 'dummy', token + time + demoBaseUrl
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: set headers using json
    * headers { Authorization: '#(token + time + demoBaseUrl)' }
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: set multi-value headers using json
    * headers { Authorization: ['dummy', '#(token + time + demoBaseUrl)'] }
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: set multi-value headers using function call
    # this is a test case for an edge case where commas in json confuse cucumber
    * def fun = function(arg){ return [arg.first, arg.second] }
    * header Authorization = call fun { first: 'dummy', second: '#(token + time + demoBaseUrl)' }
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200
