Feature:

Background:
* driver serverUrl + '/00'
* def dimensions = karate.get('dimensions')
* if (dimensions) driver.dimensions = dimensions

Scenario:
# driver.url | driver.title | waitForUrl() | refresh() | back() | forward() | driver.dimensions
* call read('01.feature')

# waitFor() | waitForText() | waitForEnabled()
* call read('02.feature')

# script() | waitUntil()
* call read('03.feature')

# cookies
* call read('04.feature')

# driver.intercept
* if (driverType == 'chrome') karate.call('05.feature')

# position()
* call read('06.feature')

# input() | value() | text() | html()
* call read('07.feature')

# switchFrame()
* call read('08.feature')

# friendly locators
* call read('09.feature')
