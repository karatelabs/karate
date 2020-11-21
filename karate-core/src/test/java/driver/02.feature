Feature:

Background:
* driver serverUrl
* click('{}02')

Scenario:
# wait for slow loading element
* waitFor('#slowDiv')

# this is a string "contains" match for convenience
* waitForText('#slowDiv', 'APPEARED')

# how to search the whole html
* waitForText('body', 'APPEARED')

# will be false if disabled
* waitForEnabled('#slowDiv')