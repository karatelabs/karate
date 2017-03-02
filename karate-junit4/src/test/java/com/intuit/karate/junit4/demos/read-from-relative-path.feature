Feature: reading files

Scenario: from a relative path

* def fun = read('../syntax/for-demos.js')
* assert fun() == 'foo'



