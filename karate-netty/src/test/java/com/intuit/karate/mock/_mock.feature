@ignore
Feature:

Background:
* def uuid = function(){ return java.util.UUID.randomUUID() + '' }

Scenario: pathMatches('/v1/cats')
    * def responseStatus = 201
    * def response = { id: '#(uuid())', name: 'Billie' }

Scenario: pathMatches('/v1/cats/{uuid}')
    * def response = { id: '#(uuid())', name: 'Billie' }

Scenario: pathMatches('/v1/dogs')
    * def responseStatus = 201
    * def response = { id: '#(uuid())', name: 'Dummy' }

Scenario: pathMatches('/v1/dogs/{uuid}')
    * def response = { id: '#(uuid())', name: 'Dummy' }

Scenario: pathMatches('/v1/binary/download')
    * def responseHeaders = { 'Content-Type': 'application/octet-stream' }
    * def Runner = Java.type('com.intuit.karate.mock.MockServerTest')
    * def response = Runner.testBytes

Scenario: pathMatches('/v1/binary/upload')
    * def Runner = Java.type('com.intuit.karate.mock.MockServerTest')
    * def success = java.util.Arrays.equals(Runner.testBytes, requestBytes)
    * def response = ({ success: success })

Scenario: pathMatches('/v1/patch')
    * def responseStatus = 422
    * def response = { success: true }

Scenario: pathMatches('/v1/delete')
    * def response = { success: true }

Scenario: pathMatches('/v1/deleteEmptyResponse')
    * def response = ''

Scenario: pathMatches('/v1/commas')
    * def response = { success: true } 
    
Scenario: pathMatches('/v1/multiparams')
    * def response = { success: true } 

Scenario: pathMatches('/v1/german')
    * def response = <name>Müller</name> 

Scenario: pathMatches('/v1/encoding/{raw}')
    * def response = { success: true }

Scenario: pathMatches('/v1/linefeed')
    * def response = '\n{ "success": true }'

Scenario: pathMatches('/v1/spaces')
    * def response = '\n    \n'

Scenario: pathMatches('/v1/noheaders')    
    * def responseStatus = 404

Scenario: pathMatches('/v1/cookies')    
    * def responseHeaders = { 'Set-Cookie': 'foo=bar' }
