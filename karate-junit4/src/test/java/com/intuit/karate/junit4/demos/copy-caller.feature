Feature: the difference with variable scoping in 'isolated' and 'shared' mode
    since call is 'pass by reference' you need to clone using 'copy' if needed

Scenario: isolated scope: called feature does not over-write variables
    * def someString = 'before'
    * def someJson = { value: 'before' }
    * def result = call read('copy-called-overwrite.feature')
    * match someString == 'before'
    * match someJson == { value: 'before' }
    * assert typeof fromCalled == 'undefined'

Scenario: shared scope: called feature will over-write (and contribute) variables
    * def someString = 'before'
    * def someJson = { value: 'before' }
    * call read('copy-called-overwrite.feature')
    * match someString == 'after'
    * match someJson == { value: 'after' }
    * match fromCalled == { hello: 'world' }

Scenario: called feature updates a nested element of 'foo' using the 'set' keyword
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

Scenario: clone should be 'deep' and work even for nested data
    * def temp = call read('copy-called-nested.feature')
    * def a = temp.root
    * copy b = a
    * set b.name.name = 'copy'
    * match b.name.name == 'copy'
    * match a.name.name == 'inner'