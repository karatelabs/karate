Feature:

Scenario: pathMatches('/one')
* def response = ({ one: requestHeaders['test-id'][0] })

Scenario: pathMatches('/two')
* def response = ({ two: requestHeaders['test-id'][0] })

Scenario: pathMatches('/three')
* def response = ({ three: requestHeaders['test-id'][0] })