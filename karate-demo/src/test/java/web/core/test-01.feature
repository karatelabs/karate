Feature: ui automation core capabilities

Background:
  * def webUrlBase = karate.properties['web.url.base']

Scenario Outline: dom operations, validations and navigation
  * configure driver = <config>

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

Examples:
    | config |
    | { type: 'chrome', timeout: 5000, executable: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' } |
    | { type: 'chromedriver', port: 9515, executable: '/Users/pthomas3/dev/webdriver/chromedriver' } |
    | { type: 'geckodriver', port: 4444, executable: '/Users/pthomas3/dev/webdriver/geckodriver' } |
    | { type: 'safaridriver', port: 5555, executable: 'safaridriver' } |
    # | { type: 'mswebdriver', port: 17556, executable: 'C:/Users/pthomas3/Downloads/MicrosoftWebDriver.exe' } |
    # | { type: 'edge', timeout: 5000, executable: 'MicrosoftEdge' } |
    