Feature: intercepting browser requests

Scenario: 
# intercepting browser http requests is supported only for chrome native
* configure driver = { type: 'chrome', showDriverLog: true }

# if you need to set up the interceptor before the target page is loaded
# use 'about:blank' as the init url, e.g. "* driver 'about:blank'"
* driver 'https://www.seleniumeasy.com/test/dynamic-data-loading-demo.html'

# the mock feature supports the usual prefixes such as "classpath:"
# note that switching on "* configure cors = true" may be needed
* driver.intercept({ patterns: [{ urlPattern: '*randomuser.me/*' }], mock: 'mock-01.feature' })

# useful for demo-ing this
# * karate.stop(9000)

* click('{}Get New User')
* delay(2000)
* screenshot()
