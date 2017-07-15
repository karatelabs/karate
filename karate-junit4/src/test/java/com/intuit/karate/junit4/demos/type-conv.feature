Feature: convert between json, xml and string

Scenario: string to json
# this would be of type string (not JSON)
* def strVar = '{ "foo": "bar" }'
* json jsonVar = strVar
* match jsonVar == { foo: 'bar' }

Scenario: json to string
* def jsonVar = { foo: 'bar' }
* string strVar = jsonVar
* assert strVar == '{"foo":"bar"}'

Scenario: string to xml
* def strVar = '<root><foo>bar</foo></root>'
* xml xmlVar = strVar
* match xmlVar == <root><foo>bar</foo></root>

Scenario: xml to string (incorrect)
* def xmlVar = <root><foo>bar</foo></root>
* string strVar = xmlVar
# because of karate's internal map-like default representation, this happens. see 'xmlstring' below
* match strVar == '{"root":{"foo":"bar"}}'

Scenario: xml to string
* def xmlVar = <root><foo>bar</foo></root>
# note that the keyword here is 'xmlstring' not 'string'
* xmlstring strVar = xmlVar
* match strVar == '<root><foo>bar</foo></root>'

Scenario: xml to json
* def xmlVar = <root><foo>bar</foo></root>
* json jsonVar = xmlVar
* match jsonVar == { root: { foo: 'bar' } }

Scenario: json to xml
* def jsonVar = { root: { foo: 'bar' } }
* xml xmlVar = jsonVar
* match xmlVar == <root><foo>bar</foo></root>

Scenario: xml with attributes
* def xmlVar = <root><foo fizz="buzz">bar</foo></root>
* json jsonVar = xmlVar
# it ain't pretty but this is how karate converts xml to a map-like object internally for parity with json
* match jsonVar == { root: { foo: { _ : 'bar', @: { fizz: 'buzz' }}}}
# which means that json can be used instead of xpath
* match jsonVar $.root.foo._ == 'bar'
* match jsonVar $.root.foo.@ == { fizz: 'buzz' }
* match jsonVar $.root.foo.@.fizz == 'buzz'
* match jsonVar $..foo.@.fizz == ['buzz']
* match jsonVar $..@.fizz contains 'buzz'
* match jsonVar $..foo.@ contains { fizz: 'buzz' }

Scenario: xml with namespaces
* def xmlVar = <ns1:root xmlns:ns1="http://foo.com" xmlns:ns2="http://bar.com"><ns2:foo fizz="buzz" ping="pong">bar</ns2:foo></ns1:root>
* json jsonVar = xmlVar
* match jsonVar == 
"""
{ 
  "ns1:root": {
    "@": { "xmlns:ns1": "http://foo.com", "xmlns:ns2": "http://bar.com" },
    "_": { 
      "ns2:foo": { 
        "_": "bar", 
        "@": { "fizz": "buzz", "ping": "pong" } 
      } 
    }     
  }
}
"""
* match jsonVar $.ns1:root..ns2:foo.@ == [{ fizz: 'buzz', ping: 'pong' }]
* match jsonVar $..ns2:foo.@ == [{ fizz: 'buzz', ping: 'pong' }]
* match jsonVar $..ns2:foo.@ contains { fizz: 'buzz', ping: 'pong' }
* match jsonVar $..ns2:foo.@ contains only { fizz: 'buzz', ping: 'pong' }
* match each jsonVar $..ns2:foo.@ contains { ping: 'pong' }