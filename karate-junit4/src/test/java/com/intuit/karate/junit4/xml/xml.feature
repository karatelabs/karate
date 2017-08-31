Feature: xml samples and tests

Scenario: placeholders using xml embedded expressions
    * def search = { number: '123456', wireless: true, voip: false, tollFree: false }
    * def req = read('soap1.xml')
    * def phone = req/Envelope/Body/getAccountByPhoneNumber
    * match phone /getAccountByPhoneNumber/phoneNumber == '123456'
    * match phone ==
    """
    <acc:getAccountByPhoneNumber>
        <acc:phoneNumber>123456</acc:phoneNumber>
        <acc:phoneNumberSearchOption>
            <acc:searchWirelessInd>true</acc:searchWirelessInd>
            <acc:searchVoipInd>false</acc:searchVoipInd>
            <acc:searchTollFreeInd>false</acc:searchTollFreeInd>
        </acc:phoneNumberSearchOption>
    </acc:getAccountByPhoneNumber>
    """
    # string comparisons may be simpler than xpath in some cases
    * xmlstring reqString = req
    * match reqString contains '<acc:phoneNumber>123456</acc:phoneNumber>'

Scenario: placeholders using string replace
    * def req = read('soap2.xml')
    * replace req
        | token        | value     |
        | @@number@@   | '123456'  |
        | @@wireless@@ | 'true'    |
        | @@voip@@     | 'false'   |
        | @@tollFree@@ | 'false'   |
    # convert back to xml after a string replace
    * xml req = req
    * match req /Envelope/Body/getAccountByPhoneNumber/phoneNumber == '123456'


Scenario: set xml chunks using xpath
    * def req = read('envelope1.xml')
    * def phone = '123456'
    * def search = 
    """
    <acc:getAccountByPhoneNumber>
        <acc:phoneNumber>#(phone)</acc:phoneNumber>
    </acc:getAccountByPhoneNumber>
    """
    * set req /Envelope/Body = search
    * match req ==
    """
    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:acc="http://foo/bar">
        <soapenv:Header />
        <soapenv:Body>
            <acc:getAccountByPhoneNumber>
                <acc:phoneNumber>123456</acc:phoneNumber>
            </acc:getAccountByPhoneNumber>
        </soapenv:Body>
    </soapenv:Envelope>
    """

Scenario: set xml chunks using embedded expressions
    * def phone = '123456'
    # this will remove the <acc:phoneNumberSearchOption> element
    * def searchOption = null
    * def search = 
    """
    <acc:getAccountByPhoneNumber>
        <acc:phoneNumber>#(phone)</acc:phoneNumber>
        <acc:phoneNumberSearchOption>##(searchOption)</acc:phoneNumberSearchOption>        
    </acc:getAccountByPhoneNumber>
    """
    * def req = read('envelope2.xml')
    * match req ==
    """
    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:acc="http://foo/bar">
        <soapenv:Header />
        <soapenv:Body>
            <acc:getAccountByPhoneNumber>
                <acc:phoneNumber>123456</acc:phoneNumber>
            </acc:getAccountByPhoneNumber>
        </soapenv:Body>
    </soapenv:Envelope>
    """

Scenario: set via table
    * def search = 
    """
    <acc:getAccountByPhoneNumber>
        <acc:phoneNumber></acc:phoneNumber>
        <acc:phoneNumberSearchOption></acc:phoneNumberSearchOption>        
    </acc:getAccountByPhoneNumber>
    """
    * set search /getAccountByPhoneNumber
    | path                    | value |
    | phoneNumber             | 1234  |   
    | phoneNumberSearchOption | 'all' |
    * match search ==
    """
    <acc:getAccountByPhoneNumber>
        <acc:phoneNumber>1234</acc:phoneNumber>
        <acc:phoneNumberSearchOption>all</acc:phoneNumberSearchOption>        
    </acc:getAccountByPhoneNumber>
    """

Scenario: pretty print xml
    * def search = { number: '123456', wireless: true, voip: false, tollFree: false }
    * def xml = read('soap1.xml')
    * print 'pretty print:\n' + karate.prettyXml(xml)

