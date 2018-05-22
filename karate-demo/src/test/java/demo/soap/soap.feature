Feature: test soap end point

Background:
* url demoBaseUrl + '/soap'
# this live url should work if you want to try this on your own
# * url 'http://www.dneonline.com/calculator.asmx'

Scenario: soap 1.1
    Given request
    """
    <?xml version="1.0" encoding="utf-8"?>
    <soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
      <soap:Body>
        <Add xmlns="http://tempuri.org/">
          <intA>2</intA>
          <intB>3</intB>
        </Add>
      </soap:Body>
    </soap:Envelope>
    """
    When soap action 'http://tempuri.org/Add'
    Then status 200
    And match /Envelope/Body/AddResponse/AddResult == 5
    And print 'response: ', response

Scenario: soap 1.2
    Given request read('request.xml')
    # soap is just an HTTP POST, so here we set the required header manually ..
    And header Content-Type = 'application/soap+xml; charset=utf-8'
    # .. and then we use the 'method keyword' instead of 'soap action'
    When method post
    Then status 200
    # note how we focus only on the relevant part of the payload and read expected XML from a file
    And match /Envelope/Body/AddResponse == read('expected.xml')
