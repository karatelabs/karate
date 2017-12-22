Feature: call is pass by reference so you need to clone if needed

Scenario: called feature clobbered json in caller
    * def foo = { key: 'value' }
    # by default, complex data (JSON, XML, MAP, LIST) are passed by reference
    * def result = call read('copy-called.feature')
    # so callers can mutate this context !
    * match foo == { key: 'changed' }

Scenario: you can manually 'clone' a payload if needed
    * def original = { key: 'value' }
    # since the called feature mutates 'foo' we ensure it is a clone
    * copy foo = original
    * def result = call read('copy-called.feature')
    # and original remains unchanged
    * match original == { key: 'value' }    