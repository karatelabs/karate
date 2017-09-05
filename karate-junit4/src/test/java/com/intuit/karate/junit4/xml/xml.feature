Feature: xml samples and tests

Scenario: pretty print xml
    * def search = { number: '123456', wireless: true, voip: false, tollFree: false }
    * def xml = read('soap1.xml')
    * print 'pretty print:\n' + karate.prettyXml(xml)

Scenario: test removing elements using keyword
    * def base = <query><name>foo</name></query>
    * remove base /query/name
    * match base == <query/>

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
        <soapenv:Header/>
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

Scenario: set via table, mixing xml chunks
    * set search /acc:getAccountByPhoneNumber
    | path                        | value |
    | acc:phoneNumber             | 1234  |
    | acc:foo    | <acc:bar>baz</acc:bar> |

    * match search ==
    """
    <acc:getAccountByPhoneNumber>
        <acc:phoneNumber>1234</acc:phoneNumber>
        <acc:foo>
            <acc:bar>baz</acc:bar>
        </acc:foo>        
    </acc:getAccountByPhoneNumber>
    """

Scenario: set via table, build xml including attributes and repeated elements
    * set search /acc:getAccountByPhoneNumber
    | path                        | value |
    | acc:phone/@foo              | 'bar' |
    | acc:phone/acc:number[1]     | 1234  |
    | acc:phone/acc:number[2]     | 5678  |     
    | acc:phoneNumberSearchOption | 'all' |

    * match search ==
    """
    <acc:getAccountByPhoneNumber>
        <acc:phone foo="bar">
            <acc:number>1234</acc:number>
            <acc:number>5678</acc:number>
        </acc:phone>
        <acc:phoneNumberSearchOption>all</acc:phoneNumberSearchOption>        
    </acc:getAccountByPhoneNumber>
    """

Scenario Outline: conditionally build xml from scenario-outline and examples
    * def xml = 
    """
    <query>
      <name>
        <firstName>##(<_firstName>)</firstName>
        <lastName>##(<_lastName>)</lastName>
      </name>
      <age>##(<_age>)</age>
    </query>
    """

    * match xml == <_expected>

    Examples:
    | _firstName | _lastName | _age | _expected                                                                                      |
    | 'John'     | 'Smith'   |   20 | <query><name><firstName>John</firstName><lastName>Smith</lastName></name><age>20</age></query> |
    | 'Jane'     | 'Doe'     | null | <query><name><firstName>Jane</firstName><lastName>Doe</lastName></name></query>                |
    | null       | 'Waldo'   | null | <query><name><lastName>Waldo</lastName></name></query>                                         |


Scenario: a cleaner way to achieve the above by using tables and the 'set' keyword
    * set search /queries/query
        | path           | 1        | 2      | 3       |
        | name/firstName | 'John'   | 'Jane' |         |
        | name/lastName  | 'Smith'  | 'Doe'  | 'Waldo' |
        | age            | 20       |        |         |
        
    * match search/queries/query[1] == <query><name><firstName>John</firstName><lastName>Smith</lastName></name><age>20</age></query>
    * match search/queries/query[2] == <query><name><firstName>Jane</firstName><lastName>Doe</lastName></name></query>
    * match search/queries/query[3] == <query><name><lastName>Waldo</lastName></name></query>
    


