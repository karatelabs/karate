@ignore
Feature: called file should not clobber vars in caller

Scenario:
* match foo == { key: 'value' }
* set foo.key = 'changed'
* match foo == { key: 'changed' }
