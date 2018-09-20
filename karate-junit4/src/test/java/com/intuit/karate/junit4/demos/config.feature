Feature: json and js functions loaded in the karate-config.js

Scenario: complex 'global' json
    * match myObject == { error: [{id: 1},{id: 2}] }

Scenario: a global js function
    * assert myFunction() == 'hello world'


