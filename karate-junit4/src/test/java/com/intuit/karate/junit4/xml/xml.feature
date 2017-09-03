Feature: xml samples and tests

Scenario: pretty print xml
    * def search = { number: '123456', wireless: true, voip: false, tollFree: false }
    * def xml = read('soap1.xml')
    * print 'pretty print:\n' + karate.prettyXml(xml)

Scenario: test removing elements from xml from js
    * def base = <query><name>foo</name></query>
    * def fun = function(){ karate.remove('base', '/query/name') }
    * call fun
    * match base == <query/>

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

Scenario: set via table, variable and xml nodes will be auto-built
    * set search /acc:getAccountByPhoneNumber
    | path                        | value |
    | acc:phoneNumber             | 1234  |   
    | acc:phoneNumberSearchOption | 'all' |

    * match search ==
    """
    <acc:getAccountByPhoneNumber>
        <acc:phoneNumber>1234</acc:phoneNumber>
        <acc:phoneNumberSearchOption>all</acc:phoneNumberSearchOption>        
    </acc:getAccountByPhoneNumber>
    """

Scenario Outline: conditionally build xml from scenario-outline and examples
    * def firstName = '<_firstName>' || null
    * def lastName = '<_lastName>' || null
    * def age = '<_age>' || null

    * def xml = 
    """
    <query>
      <name>
        <firstName>##(firstName)</firstName>
        <lastName>##(lastName)</lastName>
      </name>
      <age>##(age)</age>
    </query>
    """

    * match xml == <_expected>

    Examples:
    | _firstName | _lastName | _age | _expected                                                                                      |
    | John       | Smith     |   20 | <query><name><firstName>John</firstName><lastName>Smith</lastName></name><age>20</age></query> |
    | Jane       | Doe       |      | <query><name><firstName>Jane</firstName><lastName>Doe</lastName></name></query>                |
    |            | Waldo     |      | <query><name><lastName>Waldo</lastName></name></query>                                         |
