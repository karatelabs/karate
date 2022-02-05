Feature:

Background:
* driver serverUrl + '/10'

Scenario: scroll to a text and type in the input on the right
* waitFor('{}this test verifies an element can be located even when the page needs scrolling')
* scroll('{}Label without scroll :').input('it works')

# TODO rightOf() not working only on firefox (is apple m1 the reason ?)
* if (driverType == 'geckodriver') karate.abort()
* scroll('{}Label with scroll :').rightOf().input('it should not fail')
