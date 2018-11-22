Feature: file upload end-point

Background:
* url demoBaseUrl

Scenario: upload file
    Given path 'files'
    # refer to the second scenario in this file for how to set the upload filename using the 'multipart file' syntax
    And multipart field myFile = read('test.pdf')
    And multipart field message = 'hello world'
    When method post
    Then status 200
    And match response == { id: '#uuid', filename: 'myFile', message: 'hello world', contentType: 'application/octet-stream' }
    And def id = response.id

    Given path 'files', id
    When method get
    Then status 200
    And match response == read('test.pdf')
    And match header Content-Disposition contains 'attachment'
    And match header Content-Disposition contains 'myFile'
    And match header Content-Type == 'application/octet-stream'

    # example of calling custom java code from karate
    * def FileChecker = Java.type('com.intuit.karate.demo.util.FileChecker')
    # example of parsing a string into json by karate
    * json fileInfo = FileChecker.getMetadata(id)
    * match fileInfo == { id: '#(id)', filename: 'myFile', message: 'hello world', contentType: 'application/octet-stream' }

@mock-servlet-todo
Scenario: upload with filename and content-type specified
    Given path 'files'
    And multipart file myFile = { read: 'test.pdf', filename: 'upload-name.pdf', contentType: 'application/pdf' }
    And multipart field message = 'hello world'
    When method post
    Then status 200
    And match response == { id: '#uuid', filename: 'upload-name.pdf', message: 'hello world', contentType: 'application/pdf' }
    And def id = response.id

    Given path 'files', id
    When method get
    Then status 200
    And match responseBytes == read('test.pdf')
    And match header Content-Disposition contains 'attachment'
    And match header Content-Disposition contains 'upload-name.pdf'
    And match header Content-Type == 'application/pdf'

Scenario: upload with content created dynamically
    Given path 'files'
    And def value = 'lorem ipsum'
    And multipart file myFile = { value: '#(value)', filename: 'hello.txt', contentType: 'text/plain' }
    And multipart field message = 'hello world'
    When method post
    Then status 200
    And match response contains { id: '#uuid', filename: 'hello.txt', message: 'hello world' }
    And def id = response.id

    Given path 'files', id
    When method get
    Then status 200
    And match response == 'lorem ipsum'
    And match header Content-Disposition contains 'attachment'
    And match header Content-Disposition contains 'hello.txt'
    And match header Content-Type contains 'text/plain'

Scenario: upload multipart/mixed
    Given path 'files', 'mixed'
    And multipart field myJson = { text: 'hello world' }
    And multipart file myFile = { read: 'test.pdf', filename: 'upload-name.pdf', contentType: 'application/pdf' }
    And header Content-Type = 'multipart/mixed'
    When method post
    Then status 200
    And match response == { id: '#uuid', filename: 'upload-name.pdf', message: 'hello world', contentType: 'application/pdf' }

@mock-servlet-todo
Scenario: multipart upload has content-length header set
    Given path 'search', 'headers'
    And multipart field myFile = read('test.pdf')
    And multipart field message = 'hello world'
    When method post
    Then status 200
    And match response['content-length'][0] == '#notnull'

    Given path 'search', 'headers'
    And multipart file myFile = { read: 'test.pdf', filename: 'upload-name.pdf', contentType: 'application/pdf' }
    And multipart field message = 'hello world'
    When method post
    Then status 200
    And match response['content-length'][0] == '#notnull'