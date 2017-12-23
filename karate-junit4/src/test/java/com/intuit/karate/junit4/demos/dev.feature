@ignore
Feature: scratch pad to work on only one construct at a time

Scenario: test
* def foo = { a: 1 }
* match foo == { a: '##notnull' }
