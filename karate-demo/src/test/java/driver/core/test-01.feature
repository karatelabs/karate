Feature: ui automation core capabilities

Background:
  * def webUrlBase = karate.properties['web.url.base']

Scenario Outline: using <config>
  * def config = <config>
  * set config.showDriverLog = true
  * configure driver = config

  Given driver webUrlBase + '/page-01'
  
  # wait for very slow loading element
  And assert driver.wait('#eg01WaitId')
    
  # cookies
  * def cookie1 = { name: 'foo', value: 'bar' }
  And match driver.cookies contains '#(^cookie1)'
  And match driver.cookie('foo') contains cookie1

  # set window size
  And driver.dimensions = <dimensions>

  # navigation and dom checks
  And driver.input('#eg01InputId', 'hello world')
  When driver.click('input[name=eg01SubmitName]')
  Then match driver.text('#eg01DivId') == 'hello world'
  And match driver.value('#eg01InputId') == 'hello world'  
  And match driver.attribute('#eg01SubmitId', 'type') == 'submit'
  And match driver.name('#eg01SubmitId') == 'INPUT'
  And match driver.enabled('#eg01InputId') == true
  And match driver.enabled('#eg01DisabledId') == false

  # clear before input
  When driver.input('#eg01InputId', 'something else', true)
  And match driver.value('#eg01InputId') == 'something else'  
  When driver.value('#eg01InputId', 'something more')
  And match driver.value('#eg01InputId') == 'something more'
  
  # refresh
  When driver.refresh()
  Then match driver.location == webUrlBase + '/page-01'
  And match driver.text('#eg01DivId') == ''
  And match driver.value('#eg01InputId') == ''
  And match driver.title == 'Page One'

  # navigate to page and css checks
  When driver webUrlBase + '/page-02'
  Then match driver.text('.eg01Cls') == 'Class Locator Test'
  And match driver.html('.eg01Cls') == '<span>Class Locator Test</span>'
  And match driver.title == 'Page Two'
  And match driver.location == webUrlBase + '/page-02'
  And match driver.css('.eg01Cls', 'background-color') contains '(255, 255, 0'

  # set cookie
  Given def cookie2 = { name: 'hello', value: 'world' }
  When driver.cookie = cookie2
  Then match driver.cookies contains '#(^cookie2)'

  # delete cookie
  When driver.deleteCookie('foo')
  Then match driver.cookies !contains '#(^cookie1)'

  # clear cookies
  When driver.clearCookies()
  Then match driver.cookies == '#[0]'

  # back and forward
  When driver.back()
  Then match driver.location == webUrlBase + '/page-01'
  And match driver.title == 'Page One'
  When driver.forward()
  Then match driver.location == webUrlBase + '/page-02'
  And match driver.title == 'Page Two'

  # dialog - alert
  When driver.click('^Show Alert', true)
  Then match driver.dialog == 'this is an alert'
  And driver.dialog(true)

  # dialog - confirm true
  When driver.click('^Show Confirm', true)
  Then match driver.dialog == 'this is a confirm'
  And driver.dialog(false)
  And match driver.text('#eg02DivId') == 'Cancel'

  # dialog - confirm false
  When driver.click('^Show Confirm', true)
  And driver.dialog(true)
  And match driver.text('#eg02DivId') == 'OK'

  # dialog - prompt
  When driver.click('^Show Prompt', true)
  Then match driver.dialog == 'this is a prompt'
  And driver.dialog(true, 'hello world')
  And match driver.text('#eg02DivId') == 'hello world'

  # screenshot
  * def bytes = driver.screenshot('#eg02DivId')
  * karate.write(bytes, 'partial-' + config.type + '.png')

  # get element dimensions
  * match driver.rect('#eg02DivId') == { x: '#number', y: '#number', height: '#number', width: '#number' }

  # new tab opens, wait for page
  When driver.click('^New Tab')
  And driver.waitForPage()

  # switch back to first tab
  When driver.switchPage('Page Two')
  Then match driver.title == 'Page Two'
  And match driver.location contains webUrlBase + '/page-02'

  # submit - action that waits for page navigation
  When driver.submit('*Page Three')
  And match driver.title == 'Page Three'
  And match driver.location == webUrlBase + '/page-03'

  # select option with text
  Given driver.select('select[name=data1]', '^Option Two')
  And driver.click('input[value=check2]')
  When driver.submit('#eg02SubmitId')
  And match driver.text('#eg01Data1') == 'option2'
  And match driver.text('#eg01Data2') == 'check2'

  # select option containing text
  Given driver.select('select[name=data1]', '*Two')
  And driver.click('[value=check2]')
  And driver.click('[value=check1]')
  When driver.submit('#eg02SubmitId')
  And match driver.text('#eg01Data1') == 'option2'
  And match driver.text('#eg01Data2') == '["check1","check2"]'

  # select option by value
  Given driver.select('select[name=data1]', 'option2')
  When driver.submit('#eg02SubmitId')
  And match driver.text('#eg01Data1') == 'option2'

  # switch to iframe by index
  Given driver webUrlBase + '/page-04'
  And match driver.location == webUrlBase + '/page-04'
  And driver.switchFrame(0)
  When driver.input('#eg01InputId', 'hello world')
  And driver.click('#eg01SubmitId')
  Then match driver.text('#eg01DivId') == 'hello world'

  # switch back to parent frame
  When driver.switchFrame(null)
  Then match driver.text('#eg01DivId') == 'this div is outside the iframe'

  # switch to iframe by locator
  Given driver webUrlBase + '/page-04'
  And match driver.location == webUrlBase + '/page-04'
  And driver.switchFrame('#frame01')
  When driver.input('#eg01InputId', 'hello world')
  And driver.click('#eg01SubmitId')
  Then match driver.text('#eg01DivId') == 'hello world'

Examples:
    | config | dimensions |
    # | { type: 'chrome' } | { left: 0, top: 0, width: 300, height: 800 } |
    | { type: 'chromedriver' } | { left: 100, top: 0, width: 300, height: 800 } |
    | { type: 'geckodriver' } | { left: 600, top: 0, width: 300, height: 800 } |
    | { type: 'safaridriver' } | { left: 1000, top: 0, width: 300, height: 800 } |
    # | { type: 'mswebdriver' } |
    # | { type: 'msedge' } |
    