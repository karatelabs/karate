@ignore
Feature: Data setup helper

Scenario: Initialize test data
* def items = ['apple', 'banana', 'cherry']
* karate.log('initializing', items.length, 'items')
* def count = items.length
* print 'setup complete with', count, 'items'

Scenario: Generate ID
* def id = java.util.UUID.randomUUID().toString().substring(0, 8)
* print 'generated id:', id
