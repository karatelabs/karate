Feature: intercepting all requests !

Scenario: 
* configure driver = { type: 'chrome', showDriverLog: true }

# this will send every request the browser makes to the mock !
* driver 'about:blank'
* driver.intercept({ patterns: [{ urlPattern: '*' }], mock: 'mock-02.feature' })

* driver 'https://github.com/login'

# * karate.stop()

