@ignore
Feature: match failure reporting in arrays

Scenario: from a relative path

* def json = [{ foo: 1 }, { foo: 2 }, { foo: 3 }]
* match json contains [{ foo: 0 }, { foo: 2 }, { foo: 3 }]



