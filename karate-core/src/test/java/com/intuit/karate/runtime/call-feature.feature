Feature:

Scenario:
* def called = read('call-feature-called.feature')
* def data = [{ foo: 'first' }, { foo: 'second' }]
* def result = call called data
# this is a source of bugs as the java objects in scope get serialized
* def extracted = $result[*].res
* match extracted == ['first', 'second']
