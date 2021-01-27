Feature:

Background:

Scenario:
    * def params = { 'foo': 'bar' }
    * call read('js-read-called-2.feature') params

Scenario:
    * def params = { 'foo': 'bar' }
    * call read('js-read-called-3.feature') params
