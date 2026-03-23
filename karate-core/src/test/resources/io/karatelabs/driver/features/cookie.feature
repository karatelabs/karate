@lock=cookies
Feature: Cookie Tests
  Cookie management operations
  All scenarios use @lock=cookies to ensure mutual exclusion since cookies
  are shared at the browser level and parallel execution causes interference.

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/'
    * clearCookies()

  Scenario: Set and get cookie
    * cookie({ name: 'test_cookie', value: 'hello123', domain: 'host.testcontainers.internal' })
    * def c = cookie('test_cookie')
    * match c != null
    * match c.name == 'test_cookie'
    * match c.value == 'hello123'

  Scenario: Get all cookies
    * cookie({ name: 'cookie1', value: 'value1', domain: 'host.testcontainers.internal' })
    * cookie({ name: 'cookie2', value: 'value2', domain: 'host.testcontainers.internal' })
    * def cookies = driver.cookies
    * assert cookies.length >= 2
    * def names = $cookies[*].name
    * match names contains 'cookie1'
    * match names contains 'cookie2'

  Scenario: Delete cookie
    * cookie({ name: 'to_delete', value: 'delete_me', domain: 'host.testcontainers.internal' })
    * def c = cookie('to_delete')
    * match c != null
    * deleteCookie('to_delete')
    * def c = cookie('to_delete')
    * match c == null

  Scenario: Clear all cookies
    * cookie({ name: 'clear1', value: 'v1', domain: 'host.testcontainers.internal' })
    * cookie({ name: 'clear2', value: 'v2', domain: 'host.testcontainers.internal' })
    * clearCookies()
    * def cookies = driver.cookies
    * def names = $cookies[*].name
    * match names !contains 'clear1'
    * match names !contains 'clear2'

  Scenario: Cookie not found
    * def c = cookie('nonexistent')
    * match c == null

  Scenario: Cookie stress test - would cause parallel interference without @lock
    # This scenario clears cookies repeatedly. Without @lock=cookies at feature level,
    # this would interfere with other cookie tests running in parallel.
    # The @lock=cookies ensures mutual exclusion.
    * clearCookies()
    * delay(50)
    * clearCookies()
    * delay(50)
    * clearCookies()
