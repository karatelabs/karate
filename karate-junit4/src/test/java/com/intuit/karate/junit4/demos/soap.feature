@ignore
Feature: 
    sample karate test script that includes SOAP calls
    to the following demo service:
    http://www.webservicex.com/stockquote.asmx?op=GetQuote

Background:
* url 'http://www.webservicex.com/stockquote.asmx'

Scenario: soap 1.1
Given request
"""
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <GetQuote xmlns="http://www.webserviceX.NET/">
      <symbol>INTU</symbol>
    </GetQuote>
  </soap:Body>
</soap:Envelope>
"""
When soap action 'http://www.webserviceX.NET/GetQuote'
Then status 200

* def last = //GetQuoteResult
* print last




