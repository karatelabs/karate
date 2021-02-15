@ignore
Feature:

Scenario:
* def before = 'before'
* print 1 + 2
* def callresult = call read('called_2.feature@foo=bar2')
* def after = 'after'
