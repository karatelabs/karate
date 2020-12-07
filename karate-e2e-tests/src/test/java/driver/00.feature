Feature:

Background:
* driver serverUrl + '/00'

Scenario:
# driver.url | driver.title | waitForUrl() | refresh() | back() | forward() | driver.dimensions
* call read('01.feature')

# waitFor() | waitForText() | waitForEnabled()
#* call read('02.feature')

# script() | waitUntil()
#* call read('03.feature')

# cookies
#* call read('04.feature')

# driver.intercept
#* call read('05.feature')
