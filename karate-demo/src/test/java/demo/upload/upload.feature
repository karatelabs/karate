Feature: file upload end-point

Background:
* url demoBaseUrl

Scenario: upload file

Given path 'files'
And multipart field file = read('test.pdf')
And multipart field name = 'test.pdf'
When method post
Then status 200
And match response == { id: '#uuid', name: 'test.pdf' }
And def id = response.id

Given path 'files', id
When method get
Then status 200
And match response == read('test.pdf')
And match header Content-Disposition contains 'test.pdf'
And match header Content-Type == 'application/octet-stream'

# example of calling custom java code from karate
* def FileChecker = Java.type('com.intuit.karate.demo.util.FileChecker')
* assert 'test.pdf' == FileChecker.getMetadata(id)



