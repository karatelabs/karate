Feature: simple 3

Scenario: 3-one
* print '3-one'

Scenario: 3-two
* print '3-two'

Scenario: 3-three
* print '3-three'
Given url 'http://httpbin.org/get'
When method get
