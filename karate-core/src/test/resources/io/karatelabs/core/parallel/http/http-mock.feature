Feature: Mock for parallel HTTP tests

  Scenario: pathMatches('/one')
    * def response = { one: 'response-one' }

  Scenario: pathMatches('/two')
    * def response = { two: 'response-two' }

  Scenario: pathMatches('/three')
    * def response = { three: 'response-three' }

  Scenario: pathMatches('/fromconfig')
    * def response = { message: 'from config' }

  Scenario: pathMatches('/fromfeature')
    * def response = { message: 'from feature' }

  Scenario: pathMatches('/echo/{id}')
    # Extract test-id header if present, otherwise use 'none'
    * def testIdHeader = requestHeaders['test-id']
    * def testId = testIdHeader ? testIdHeader[0] : 'none'
    # Extract cookie-id if present, otherwise use 'none'
    * def cookieId = requestCookies['cookie-id'] ? requestCookies['cookie-id'].value : 'none'
    * def response = { id: pathParams.id, testId: testId, cookieId: cookieId }
