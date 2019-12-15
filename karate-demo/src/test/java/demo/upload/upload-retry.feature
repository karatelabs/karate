Feature: file upload retry

Background:
* url demoBaseUrl

Scenario: upload file
    * def count = 0
    * def done = function(){ var temp = karate.get('count'); temp = temp + 1; karate.set('count', temp); return temp > 1 }
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
