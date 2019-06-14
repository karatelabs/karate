Feature: test reading java properties files

Scenario: read properties
    * def stream = read('test.properties')
    * def props = new java.util.Properties()
    * props.load(stream)
    * match props == { hello: 'world', 'some.value': 'foo', 'some.other.value': 'bar' }


