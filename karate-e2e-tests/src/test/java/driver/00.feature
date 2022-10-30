Feature:

Background:
* driver serverUrl + '/00'
* def dimensions = karate.get('dimensions')
* if (dimensions) driver.dimensions = dimensions
* def skipSlowTests = karate.properties['skip.slow.tests']

Scenario:
# driver.send() (has to be first)
# * if (driverType == 'chrome') karate.call('12.feature')

# driver.url | driver.title | waitForUrl() | refresh() | back() | forward() | driver.dimensions
# * call read('01.feature')

# waitFor() | waitForText() | waitForEnabled()
* call read('02.feature')

# script() | waitUntil()
* call read('03.feature')

# cookies
* if (driverType != 'safaridriver') karate.call('04.feature')

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

# element position
* call read('10.feature')

* if (driverType == 'playwright') karate.abort()

# switchPage()
* if (driverType == 'chrome' && !skipSlowTests) karate.call('11.feature')

# switchPage() with external URLs
* if (driverType == 'chrome' && !skipSlowTests) karate.call('13.feature')

# survive Target.detachedFromTarget with nested iframes
* if (driverType == 'chrome' && !skipSlowTests) karate.call('14.feature')

# xpath locators
* call read('15.feature')

# image comparison
* if (!skipSlowTests) karate.call('16.feature')

# switch to root session on page close
* call read('17.feature')