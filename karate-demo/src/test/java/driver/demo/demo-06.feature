Feature: browser automation 1

Background:
  * configure driver = { type: 'chrome', showDriverLog: true }
  # * configure driverTarget = { docker: 'justinribeiro/chrome-headless', showDriverLog: true }
  # * configure driverTarget = { docker: 'ptrthomas/karate-chrome', showDriverLog: true }
  # * configure driver = { type: 'chromedriver', showDriverLog: true }
  # * configure driver = { type: 'geckodriver', showDriverLog: true }
  # * configure driver = { type: 'safaridriver', showDriverLog: true }
  # * configure driver = { type: 'iedriver', showDriverLog: true, httpConfig: { readTimeout: 120000 } }
  * url demoBaseUrl

 Scenario: pass cookie from API call to UI call
  Given path 'search', 'cookies'
  * cookies { someKey: 'someValue', foo: 'bar' }
  When method get
  Then status 200
  And match response == '#[2]'
  And match response[0] contains { name: 'foo', value: 'bar' }

  Given driver demoBaseUrl + '/search/cookies'
  * print responseCookies
  # set responseCookies from API call to UI(driver)
  When setCookies(responseCookies)
  Then match driver.cookies == '#[2]'

Scenario: pass cookie from a UI call to a certain API call
  Given driver demoBaseUrl + '/search/cookies'
  Given def cookie2 = { name: 'hello', value: 'world' }
  When cookie(cookie2)
  Then match driver.cookies contains '#(^cookie2)'

  Given path 'search', 'cookies'
  # set driver cookies for api call
  * cookies driver.cookies
  When method get
  Then status 200
  And match response == '#[1]'
  And match response[0] contains { name: 'hello', value: 'world' }

Scenario: pass cookie from a UI call to a certain API call - negative
  Given driver demoBaseUrl + '/search/cookies'
  Given def cookie2 = { name: 'hello', value: 'world' }
  When cookie(cookie2)
  Then match driver.cookies contains '#(^cookie2)'

  When clearCookies()
  Then match driver.cookies == '#[0]'

  Given path 'search', 'cookies'
  * cookies driver.cookies
  When method get
  Then status 200
  And match response == '#[0]'




