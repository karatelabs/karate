Feature: lower case helper method

Scenario: json
* def json = { FOO: 'BAR', Hello: 'World' }
* def json = karate.lowerCase(json)
* match json == { foo: 'bar', hello: 'world' }

Scenario: xml
* def xml = <Foo><BAR NAME="Blah">baZ</BAR></Foo>
* xml xml = karate.lowerCase(xml)
* match xml == <foo><bar name="blah">baz</bar></foo>
