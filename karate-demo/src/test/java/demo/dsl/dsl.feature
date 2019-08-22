Feature: how you can define custom keywords

Background:
* call read('common.feature')

Scenario: re-using code in a readable style
  # invoke js function
  * quack()

  # call js function
  * call greet 'John'

  # call feature
  * call login { name: 'John', type: 'admin' }
