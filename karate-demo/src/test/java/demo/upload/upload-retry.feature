@mock-servlet-todo
Feature: file upload retry

Background:
* url demoBaseUrl

Scenario: upload file
    * def count = { value: 0 }
    * configure retry = { interval: 100 }
    * def done = function(){ return count.value++ == 1 }
    Given path 'files'    
    And multipart file myFile = { read: 'test.pdf', filename: 'upload-name.pdf', contentType: 'application/pdf' }
    And multipart field message = 'hello world'
    And retry until done()
    When method post
    Then status 200
    And match response == { id: '#uuid', filename: 'upload-name.pdf', message: 'hello world', contentType: 'application/pdf' }
    And def id = response.id

    Given path 'files', id
    When method get
    Then status 200
    And match response == read('test.pdf')
