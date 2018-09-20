Feature: json and js functions loaded in the karate-config.js

Scenario: complex 'global' json
    * match myObject == { error: [{id: 1},{id: 2}] }

Scenario: a global js function
    * assert myFunction() == 'hello world'

Scenario: from a feature, global / shared scope
    * call read('common.feature')
    * assert hello() == 'hello'
    * assert world() == 'world'

Scenario: from a feature, isolated / name-spaced scope
    * def utils = call read('common.feature')
    * assert utils.hello() == 'hello'
    * assert utils.world() == 'world'
