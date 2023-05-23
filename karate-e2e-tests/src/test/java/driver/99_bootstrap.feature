Feature: 

Background:
* driver serverUrl + '/99_bootstrap'

Scenario: using the mouse to click and select something in a js-powered dropdown

# can help for some pages that take time to load
* waitUntil("document.readyState == 'complete'")

# click on button to show dropdown options
* mouse('button').click()

# click on first option
* mouse('a.dropdown-item').click()

# asserted expected result
* match text('#container') == 'First'


Scenario: looping over data to repeat an action

# can help for some pages that take time to load
* waitUntil("document.readyState == 'complete'")

# get all possible drop-down elements
* def list = locateAll('a.dropdown-item')
* def results = []
* def fun =
"""
function(e) {
    mouse('button').click();
    e.mouse().click();
    let result = text('#container').trim();
    results.push(result);
    delay(2000);
}
"""

# perform the loop to click on all dropdown items
* list.forEach(fun)

# assert at the end for the data collected
* match results == ['First', 'Second', 'Third']

