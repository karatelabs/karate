Feature:

Scenario:
* def foo = call read('call-by-tag-called.feature@name=second')
* match foo.bar == 2

Scenario:
* def foo = call read('@sameFileTag')
* match foo.bar == 2

@ignore @sameFileTag
Scenario:
* call read('call-by-tag-called.feature@name=second')
* match bar == 2