Feature:

Background:
* driver serverUrl + '/10'

Scenario: scroll to a text and type in the input on the right
* waitFor('{}this test verifies an element can be located even when the page needs scrolling')
* scroll('{}Label without scroll :').input('it works')
* scroll('{}Label with scroll :').rightOf().input('it should not fail')
