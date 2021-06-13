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
    * def Utils = Java.type('com.intuit.karate.core.MockUtils')
    * def response = Utils.testBytes

Scenario: pathMatches('/v1/binary/upload')
    * def Utils = Java.type('com.intuit.karate.core.MockUtils')
    * def success = java.util.Arrays.equals(Utils.testBytes, requestBytes)
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

Scenario: pathMatches('/v1/download')
    * def response = read('test.pdf.zip')

Scenario: pathMatches('/v1/upload')
    * def response = { size: '#(requestBytes.length)' }

Scenario: pathMatches('/v1/upload/excel')
    * def filePart = requestParts['myFile'][0]
    * def response = filePart

Scenario: pathMatches('/v1/multipart')
    * def response = { success: true }

Scenario: pathMatches('/v1/form')
    # TODO urlencoded form handling on server side
    * def response = { success: true }

Scenario: pathMatches('/v1/headers') && karate.get('requestHeaders.val[0]') == 'foo'
    * def response = { val: 'foo' }

Scenario: pathMatches('/v1/headers') && headerContains('val', 'bar')
    * def response = { val: 'bar' }

Scenario: pathMatches('/v1/malformed')
    * def response = read('malformed.txt')

Scenario: pathMatches('/v1/jsonformed')
    * def response = { hello: 'world' }

Scenario: pathMatches('/v1/xmlformed')
    * def response = <hello>world</hello>

Scenario: pathMatches('/v1/stringformed')
    * def response = 'hello world'