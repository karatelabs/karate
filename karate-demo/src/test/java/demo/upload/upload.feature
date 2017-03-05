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

Given path 'files', response.id
When method get
Then status 200
And match response == read('test.pdf')
And match header Content-Disposition contains 'test.pdf'
And match header Content-Type == 'application/octet-stream'



