@ignore
Feature: 
    sample karate test script that includes SOAP calls
    to the following demo service:
    http://www.dneonline.com/calculator.asmx?op=Add

Background:
* url 'http://www.dneonline.com'
* path 'calculator.asmx'

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

Scenario: soap 1.2
    Given request
    """
    <?xml version="1.0" encoding="utf-8"?>
    <soap12:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:soap12="http://www.w3.org/2003/05/soap-envelope">
      <soap12:Body>
        <Add xmlns="http://tempuri.org/">
          <intA>2</intA>
          <intB>3</intB>
        </Add>
      </soap12:Body>
    </soap12:Envelope>
    """
    And header Content-Type = 'application/soap+xml; charset=utf-8'
    When method post
    Then status 200
    And match /Envelope/Body/AddResponse/AddResult == 5



