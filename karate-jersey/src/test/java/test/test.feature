Feature:

Scenario:
    * url 'https://postman-echo.com/gzip'
    Given header Accept-Encoding = 'gzip'
    When method get
    Then status 200
