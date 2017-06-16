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

Scenario: xml to string
* def xmlVar = <root><foo>bar</foo></root>
# note that the keyword here is 'xmlstring' not 'string'
* xmlstring strVar = xmlVar
* assert strVar == '<root><foo>bar</foo></root>'

Scenario: xml to json
* def xmlVar = <root><foo>bar</foo></root>
* json jsonVar = xmlVar
* match jsonVar == { root: { foo: 'bar' } }

Scenario: json to xml
* def jsonVar = { root: { foo: 'bar' } }
* xml xmlVar = jsonVar
* match xmlVar == <root><foo>bar</foo></root>
