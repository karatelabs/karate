Feature: ui automation core capabilities

Background:
  * def webUrlBase = karate.properties['web.url.base']

Scenario Outline: using <config>
  * configure driver = <config>

  Given location webUrlBase + '/page-01'
    
  * def cookie1 = { name: 'foo', value: 'bar' }
  And match driver.cookies contains '#(^cookie1)'
  And match driver.cookie('foo') contains cookie1

  And driver.maximize()
  And driver.dimensions = <dimensions>

  And input #eg01InputId = 'hello world'
  When click input[name=eg01SubmitName]
  Then match driver.text('#eg01DivId') == 'hello world'
  And match driver.value('#eg01InputId') == 'hello world'  
  
  When driver.refresh()
  Then match driver.location == webUrlBase + '/page-01'
  And match driver.text('#eg01DivId') == ''
  And match driver.value('#eg01InputId') == ''
  And match driver.title == 'Page One'

  When location webUrlBase + '/page-02'
  Then match driver.text('.eg01Cls') == 'Class Locator Test'
  And match driver.html('.eg01Cls') == '<span>Class Locator Test</span>'
  And match driver.title == 'Page Two'
  And match driver.location == webUrlBase + '/page-02'   

  Given def cookie2 = { name: 'hello', value: 'world' }
  When driver.cookie = cookie2
  Then match driver.cookies contains '#(^cookie2)'

  When driver.deleteCookie('foo')
  Then match driver.cookies !contains '#(^cookie1)'

  When driver.clearCookies()
  Then match driver.cookies == '#[0]'

  When driver.back()
  Then match driver.location == webUrlBase + '/page-01'
  And match driver.title == 'Page One'

  When driver.forward()
  Then match driver.location == webUrlBase + '/page-02'
  And match driver.title == 'Page Two'

  When driver.click('^Show Alert', false)
  And driver.dialog(true)

  When submit *Page Three
  And match driver.title == 'Page Three'
  And match driver.location == webUrlBase + '/page-03'

Examples:
    | config | dimensions |
    | { type: 'chrome', start: true } | { left: 0, top: 0, width: 300, height: 800 } |
    | { type: 'chromedriver', start: true } | { left: 300, top: 0, width: 300, height: 800 } |
    | { type: 'geckodriver', start: true } | { left: 600, top: 0, width: 300, height: 800 } |
    # | { type: 'safaridriver', start: true } | { left: 700, top: 0, width: 300, height: 800 } |
    # | { type: 'mswebdriver', port: 17556, start: true } |
    # | { type: 'msedge', timeout: 5000, start: true } |
    