Feature: file upload end-point

Background:
* url demoBaseUrl

Scenario: upload binary image
    Given path 'files'
    And multipart file myFile = { read: 'karate-logo.jpg', filename: 'karate-logo.jpg', contentType: 'image/jpg' }
    And multipart field message = 'image test'
    When method post
    Then status 200
    And match response == { id: '#uuid', filename: 'karate-logo.jpg', message: 'image test', contentType: 'image/jpg' }
    And def id = response.id

    Given path 'files', id
    When method get
    Then status 200
    And match response == read('karate-logo.jpg')
    And match header Content-Disposition contains 'attachment'
    And match header Content-Disposition contains 'karate-logo.jpg'
    And match header Content-Type == 'image/jpg'
