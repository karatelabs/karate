Feature:

Scenario:
* def foo = call read('call-by-tag-called.feature@name=second')
* match foo.bar == 2
