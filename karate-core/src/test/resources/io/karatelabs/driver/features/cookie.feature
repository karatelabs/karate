Feature: Cookie Tests
  Cookie management operations
  Runs unlocked: each pooled driver owns an incognito browser context, so it has
  its own cookie jar and these scenarios cannot see or clear each other's cookies.
  Previously @lock=* because cookies were shared browser-wide — every scenario's
  pooled reset called clearCookies(), which was context-wide, so it wiped whatever
  was running in parallel. The "set races its read, cookie reads back null" that
  outlived @lock=render was that same wipe, not a timing race.

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

  Scenario: setCookies bulk-applies a list
    * def list =
    """
    [
      { name: 'bulk1', value: 'v1', domain: 'host.testcontainers.internal' },
      { name: 'bulk2', value: 'v2', domain: 'host.testcontainers.internal' }
    ]
    """
    * setCookies(list)
    * match cookie('bulk1').value == 'v1'
    * match cookie('bulk2').value == 'v2'

  Scenario: Cookie stress test - repeated clears must stay scenario-local
    # Clears cookies repeatedly. This used to interfere with every other cookie
    # scenario running in parallel, because the clear was browser-context-wide.
    # The driver's own incognito context now bounds the blast radius to this scenario.
    * clearCookies()
    * delay(50)
    * clearCookies()
    * delay(50)
    * clearCookies()
