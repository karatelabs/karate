Feature: ui automation core capabilities

Background:
  * configure driver = { type: 'chrome', timeout: 5000, executable: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' }
  # * configure driver = { type: 'chromedriver', port: 9515, executable: '/Users/pthomas3/dev/webdriver/chromedriver' }
  # * configure driver = { type: 'geckodriver', port: 4444, executable: '/Users/pthomas3/dev/webdriver/geckodriver' }
  # * configure driver = { type: 'mswebdriver', port: 17556, executable: 'C:/Users/pthomas3/Downloads/MicrosoftWebDriver.exe' }
  # * configure driver = { type: 'edge', timeout: 5000 }
  * def webUrlBase = karate.properties['web.url.base']

Scenario: dom operations, validations and navigation

  Given location webUrlBase + '/page-01'
  And input #eg01InputId = 'hello world'
  When click input[name=eg01SubmitName]
  Then match driver.text('#eg01DivId') == 'hello world'
  And match driver.value('#eg01InputId') == 'hello world'
  And match driver.title == 'Page One'
  And match driver.location == webUrlBase + '/page-01'

  When location webUrlBase + '/page-02'
  Then match driver.text('.eg01Cls') == 'Class Locator Test'
  And match driver.html('.eg01Cls') == '<span>Class Locator Test</span>'
  And match driver.title == 'Page Two'
  And match driver.location == webUrlBase + '/page-02'