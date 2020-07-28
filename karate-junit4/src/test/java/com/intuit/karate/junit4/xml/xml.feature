Feature: xml samples and tests

Scenario: xml empty elements and null
    * def foo = <root><bar/></root>
    # unfortunately XML does not have a concept of a null value
    # empty tags are always considered to have the text value of ''
    * match foo == <root><bar></bar></root>
    * match foo/root/bar == ''
    * match foo/root/bar == '#present'
    # check if a path does not exist
    * match foo/root/nope == '#notpresent'

Scenario: simple fuzzy matching
    * def xml = <root><hello>world</hello><foo>bar</foo></root>
    * match xml == <root><hello>world</hello><foo>#ignore</foo></root>
    * def xml = <root><hello foo="bar">world</hello></root>
    * match xml == <root><hello foo="#ignore">world</hello></root>

Scenario: pretty print xml
    * def search = { number: '123456', wireless: true, voip: false, tollFree: false }
    * def xml = read('soap1.xml')
    * print 'pretty print:', xml

Scenario: test removing and adding elements / attributes
    * def base = <query><name>foo</name></query>
    * remove base /query/name
    * match base == <query/>
    * set base /query/foo = 'bar'
    * set base /query/@baz = 'ban'
    * match base == <query baz="ban"><foo>bar</foo></query>
    * remove base /query/@baz
    * match base == <query><foo>bar</foo></query>

Scenario: test removing elements from xml from js
    * def base = <query><name>foo</name></query>
    * karate.remove('base', '/query/name')
    * match base == <query/>

Scenario: dynamic xpath that uses variables
    * def xml = <query><name><foo>bar</foo></name></query>
    * def elementName = 'name'
    * def name = karate.xmlPath(xml, '/query/' + elementName + '/foo')
    * match name == 'bar'
    * def queryName = karate.xmlPath(xml, '/query/' + elementName)
    * match queryName == <name><foo>bar</foo></name>
    * def foo = <root><a>1</a><a>2</a></root>
    * def tmp = karate.xmlPath(foo, 'count(/root/a)')
    * match tmp == 2

Scenario: placeholders using xml embedded expressions
    * def phoneNumber = '123456'
    * def search = { wireless: true, voip: false, tollFree: false }
    * def req = read('soap1.xml')
    * def phone = $req/Envelope/Body/getAccountByPhoneNumber
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

Scenario: karate.set() is another way to conditionally modify xml
    * table data
        | first  | last    | age |
        | 'John' | 'Smith' |  20 |
        | 'Jane' | 'Doe'   |     |
        |        | 'Waldo' |     |
    * def fun =
    """
    function(v) {
      karate.setXml('temp', '<query/>');
      if (v.first) karate.set('temp', '/query/name/firstName', v.first);
      if (v.last) karate.set('temp', '/query/name/lastName', v.last);
      if (v.age) karate.set('temp', '/query/age', v.age);
      return karate.get('temp');
    }    
    """
    * call fun data[0]
    * match temp == <query><name><firstName>John</firstName><lastName>Smith</lastName></name><age>20</age></query> 
    * call fun data[1]
    * match temp == <query><name><firstName>Jane</firstName><lastName>Doe</lastName></name></query>
    * call fun data[2]
    * match temp == <query><name><lastName>Waldo</lastName></name></query>  

Scenario: creating xml with repeating elements in a loop
    * table users
      | accountNo   | subsID         | mobile       | subsType  |
      | '113888572' | '113985218890' | '1135288836' | 'asd'     |
      | '113888573' | '113985218891' | '1135288837' | 'qwe'     |
      | '113888582' | '113985218810' | '1135288846' | 'asd'     |
    
    * def xml = <users></users>
    * def fun =
    """
    function(u, i) {
      var base = '/users/user[' + (i + 1) + ']/';
      karate.set('xml', base + 'account', u.accountNo);
      karate.set('xml', base + 'mobile', u.mobile);
      karate.set('xml', base + 'type', u.subsType);
    }
    """
    * karate.forEach(users, fun)
    * match xml ==
    """
    <users>
      <user>
        <account>113888572</account>
        <mobile>1135288836</mobile>
        <type>asd</type>
      </user>
      <user>
        <account>113888573</account>
        <mobile>1135288837</mobile>
        <type>qwe</type>
      </user>
      <user>
        <account>113888582</account>
        <mobile>1135288846</mobile>
        <type>asd</type>
      </user>
    </users>
    """

Scenario: xml containing DTD reference
    * def xml = <!DOCTYPE USER SYSTEM "http://127.0.0.1:5000/login/dtd"><foo/>
    * match xml == <foo></foo>

Scenario: xml containing DTD complex
    * def xml = 
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE USER SYSTEM "http://172.20.17.74:5000/login/dtd">
    <gpOBJECT>
      <gpPARAM name="coconuts">666</gpPARAM>
    </gpOBJECT>
    """
    * match xml == <gpOBJECT><gpPARAM name="coconuts">666</gpPARAM></gpOBJECT>

Scenario: xml containing a CDATA section
    * def xml =
      """
    <ResponseSet vers="1.0" svcid="com.iplanet.am.naming" reqid="0">
        <Response><![CDATA[<NamingResponse vers="1.0" reqid="0">
                <GetNamingProfile>
                    <Attribute name="url" value="localhost"></Attribute>
                 </GetNamingProfile>
            </NamingResponse>]]>
        </Response>
    </ResponseSet>
    """
    * xml naming = $xml /ResponseSet/Response
    * match naming //Attribute[@name='url']/@value == 'localhost'

Scenario: CDATA and simple string embedded expression
    * def foo = 'hello world'
    * def xml = <bar><![CDATA[#(foo)]]></bar>
    * match xml == <bar><![CDATA[hello world]]></bar>

Scenario: CDATA and xml embedded expression
    * def foo = <bar>baz</bar>
    * def xml =
    """
    <ResponseSet vers="1.0" svcid="com.iplanet.am.naming" reqid="0">
        <Response><![CDATA[#(foo)]]></Response>
    </ResponseSet>
    """
    * match xml ==
    """
    <ResponseSet vers="1.0" svcid="com.iplanet.am.naming" reqid="0">
        <Response><![CDATA[<bar>baz</bar>]]></Response>
    </ResponseSet>
    """

Scenario: CDATA and xml string embedded expression
    * def foo = <bar>baz</bar>
    * xmlstring foo = foo
    * def xml = <ResponseSet vers="1.0" svcid="com.iplanet.am.naming" reqid="0"><Response><![CDATA[#(foo)]]></Response></ResponseSet>
    * xmlstring xml = xml
    # note that attributes get re-ordered / sorted by name
    * match xml == '<ResponseSet reqid="0" svcid="com.iplanet.am.naming" vers="1.0"><Response><![CDATA[<bar>baz</bar>]]></Response></ResponseSet>'

Scenario: two CDATA sections is tricky - but xpath returns a list
    * def response = 
    """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <ResponseSet vers="1.0" svcid="Session" reqid="1">
        <Response><![CDATA[<SessionResponse vers="1.0" reqid="0">
                <GetSession>
                    <Session sid="1">
                        <Property name="CharSet" value="UTF-8"></Property>      
                    </Session>
                </GetSession>
            </SessionResponse>]]>
        </Response>
        <Response><![CDATA[<SessionResponse vers="1.0" reqid="1">
          <AddSessionListener>
            <OK></OK>
          </AddSessionListener>
          </SessionResponse>]]>
        </Response>
    </ResponseSet>
    """
    * def temp = $response //Response
    * xml session = temp[0]
    * match session == <SessionResponse reqid="0" vers="1.0"><GetSession><Session sid="1"><Property name="CharSet" value="UTF-8"/></Session></GetSession></SessionResponse>

Scenario: xml with attributes but null value
    * def xml = <foo><bar bbb="2" aaa="1"/></foo>
    * match xml == <foo><bar bbb="2" aaa="1"/></foo>
    * xmlstring temp = xml
    # unfortunately xml attributes get re-ordered on string conversion / http request
    * match temp == '<foo><bar aaa="1" bbb="2"/></foo>'
    * def temp = karate.prettyXml(xml)
    * match temp contains '<bar aaa="1" bbb="2"/>'

Scenario: attribute embedded expression but empty / null element text
    * def request_uuid = 'foo'
    * def response =
    """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <S:Envelope xmlns:S="http://www.w3.org/2003/05/soap-envelope">
        <S:Body>
            <SucceededGetData RequestID="#(request_uuid)">some text</SucceededGetData>
            <MessageDelivered OfferID="#(request_uuid)"/>
        </S:Body>
    </S:Envelope>
    """
    * match response == 
    """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <S:Envelope xmlns:S="http://www.w3.org/2003/05/soap-envelope">
        <S:Body>
            <SucceededGetData RequestID="foo">some text</SucceededGetData>
            <MessageDelivered OfferID="foo"/>
        </S:Body>
    </S:Envelope>
    """

Scenario: attribute embedded expression but empty / null element text
    * def request_uuid = 'foo'
    * def response =
    """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <S:Envelope xmlns:S="http://www.w3.org/2003/05/soap-envelope">
        <S:Body>
            <SucceededGetData RequestID="#(request_uuid)">some text</SucceededGetData>
            <MessageDelivered OfferID="#(request_uuid)"/>
            <Test OfferID=""/>
        </S:Body>
    </S:Envelope>
    """
    * set response /Envelope/Body/Test/@OfferID = 'bar'
    * match response == 
    """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <S:Envelope xmlns:S="http://www.w3.org/2003/05/soap-envelope">
        <S:Body>
            <SucceededGetData RequestID="foo">some text</SucceededGetData>
            <MessageDelivered OfferID="foo"/>
            <Test OfferID="bar"/>
        </S:Body>
    </S:Envelope>
    """

Scenario: repeated xml elements and fuzzy matching
    since this is tricky, convert to json first

    * json response = <response><foo><bar><msg name="Hello"/><msg name="World"/></bar><bar><msg name="Hello"/><msg name="World"/></bar></foo></response>
    * json bar = <bar><msg name="Hello"/><msg name="World"/></bar>
    * match each response.response.foo.bar == bar.bar
    * match response == { response: { foo: { bar: '#[] bar.bar' } } }
    # so yes, we can express expected data in xml
    * match response == <response><foo><bar>#[] bar.bar</bar></foo></response>

Scenario: matching ignores xml prefixes
    * def search = { number: '123456', wireless: true, voip: false, tollFree: false }
    * def xml = read('soap1.xml')

    * def phoneNumberSearchOption =
    """
    <foo:phoneNumberSearchOption xmlns:foo="http://foo/bar">
        <foo:searchWirelessInd>#(search.wireless)</foo:searchWirelessInd>
        <foo:searchVoipInd>#(search.voip)</foo:searchVoipInd>
        <foo:searchTollFreeInd>#(search.tollFree)</foo:searchTollFreeInd>
    </foo:phoneNumberSearchOption>
    """
    * match xml /Envelope/Body/getAccountByPhoneNumber/phoneNumberSearchOption == phoneNumberSearchOption

Scenario: xml to map conversion should ignore comments
* def temp =
"""
<?xml version="1.0" encoding="UTF-8"?>
<!-- Copyright 2001-2019 3rd party which has been removed. All rights reserved. -->

<!-- PRODUCTION HEADER
     produced on:        machine of third party
     production time:    20190912T162512,4Z
     production module:  3rd party module
-->
<hello>world</hello>
"""
* def message = karate.xmlPath(temp, "/hello")
* match message == "world"