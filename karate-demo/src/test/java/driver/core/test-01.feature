Feature: ui automation core capabilities

Background:
  * def webUrlBase = karate.properties['web.url.base']

Scenario Outline: using <config>
  * def config = <config>
  * config.showDriverLog = true
  * configure driver = config

  Given driver webUrlBase + '/page-01'
  
  # wait for very slow loading element
  And waitFor('#eg01WaitId')

  # wait for text (is a string "contains" match for convenience)
  And waitForText('#eg01WaitId', 'APPEARED')
  And waitForText('body', 'APPEARED')
  And waitForEnabled('#eg01WaitId')

  # powerful variants of the above, call any js on the element
  And waitUntil('#eg01WaitId', "function(e){ return e.innerHTML == 'APPEARED!' }")
  And waitUntil('#eg01WaitId', "_.innerHTML == 'APPEARED!'")
  And waitUntil('#eg01WaitId', '!_.disabled')
  
  And match script('#eg01WaitId', "function(e){ return e.innerHTML }") == 'APPEARED!'
  And match script('#eg01WaitId', '_.innerHTML') == 'APPEARED!'
  And match script('#eg01WaitId', '!_.disabled') == true

  # key events and key combinations
  And input('#eg02InputId', Key.CONTROL + 'a')
  And def temp = text('#eg02DivId')
  And match temp contains '17d'
  And match temp contains '65u'
  And script('#eg02DivId', "_.innerHTML = ''")
  When input('#eg02InputId', 'aa')
  Then def temp = text('#eg02DivId')
  And match temp contains '65u' 
    
  # cookies
  * def cookie1 = { name: 'foo', value: 'bar' }
  And match driver.cookies contains '#(^cookie1)'
  And match cookie('foo') contains cookie1

  # set window size
  And driver.dimensions = <dimensions>

  # navigation and dom checks
  And input('#eg01InputId', 'hello world')
  When click('input[name=eg01SubmitName]')
  And match value('#eg01InputId') == 'hello world'
  Then match text('#eg01DivId') == 'hello world'
  And match attribute('#eg01SubmitId', 'type') == 'submit'
  And match script('#eg01SubmitId', '_.tagName') == 'INPUT'
  And match enabled('#eg01InputId') == true
  And match enabled('#eg01DisabledId') == false

  # clear before input
  When clear('#eg01InputId')
  And input('#eg01InputId', 'something else')
  And match value('#eg01InputId') == 'something else'  
  When value('#eg01InputId', 'something more')
  And match value('#eg01InputId') == 'something more'
  
  # refresh
  When refresh()
  Then match driver.url == webUrlBase + '/page-01'
  And match text('#eg01DivId') == ''
  And match value('#eg01InputId') == ''
  And match driver.title == 'Page One'

  # navigate to page and css checks
  When driver webUrlBase + '/page-02'
  Then match text('.eg01Cls') == 'Class Locator Test'
  And match html('.eg01Cls') == '<div class="eg01Cls" style="background-color: yellow"><span>Class Locator Test</span></div>'
  And match driver.title == 'Page Two'
  And match driver.url == webUrlBase + '/page-02'

  # set cookie
  Given def cookie2 = { name: 'hello', value: 'world' }
  When cookie(cookie2)
  Then match driver.cookies contains '#(^cookie2)'

  # delete cookie
  When deleteCookie('foo')
  Then match driver.cookies !contains '#(^cookie1)'

  # clear cookies
  When clearCookies()
  Then match driver.cookies == '#[0]'

  # back and forward
  When back()
  Then match driver.url == webUrlBase + '/page-01'
  And match driver.title == 'Page One'
  When forward()
  Then match driver.url == webUrlBase + '/page-02'
  And match driver.title == 'Page Two'

  # wildcard locators
  * click('{a}Click Me')
  * match text('#eg03Result') == 'A'
  * click('{^span}Me')
  * match text('#eg03Result') == 'SPAN'
  * click('{div}Click Me')
  * match text('#eg03Result') == 'DIV'
  * click('{^div:2}Click')
  * match text('#eg03Result') == 'SECOND'
  * click('{span/a}Click Me')
  * match text('#eg03Result') == 'NESTED'
  * click('{:4}Click Me')
  * match text('#eg03Result') == 'BUTTON'
  * click("{^button:2}Item")
  * match text('#eg03Result') == 'ITEM2'

  # locate and exists
  * def element = locate('{}Click Me')
  * assert element.present
  * assert exists('{}Click Me')

  # optional
  * assert !exists('#thisDoesNotExist')
  * optional('#thisDoesNotExist').click()

  # locate all
  * def elements = locateAll('{}Click Me')
  * match karate.sizeOf(elements) == 7
  * elements.get(6).click()
  * match text('#eg03Result') == 'SECOND'
  * match elements.get(3).script('_.tagName') == 'BUTTON'

  # dialog - alert
  When click('{}Show Alert')
  Then match driver.dialog == 'this is an alert'
  And dialog(true)

  # dialog - confirm true
  When click('{}Show Confirm')
  Then match driver.dialog == 'this is a confirm'
  And dialog(false)
  And match text('#eg02DivId') == 'Cancel'

  # dialog - confirm false
  When click('{}Show Confirm')
  And dialog(true)
  And match text('#eg02DivId') == 'OK'

  # dialog - prompt
  When click('{}Show Prompt')
  Then match driver.dialog == 'this is a prompt'
  And dialog(true, 'hello world')
  And match text('#eg02DivId') == 'hello world'

  # screenshot of selected element
  * screenshot('#eg02DivId')

  # get element dimensions
  * match position('#eg02DivId') contains { x: '#number', y: '#number', width: '#number', height: '#number' }

  # new tab opens
  When click('{}New Tab')

  # switch back to first tab
  When switchPage('Page Two')
  Then match driver.title == 'Page Two'
  And match driver.url contains webUrlBase + '/page-02'

  # submit - action that waits for page navigation
  When submit().click('{^}Page Three')
  And match driver.title == 'Page Three'
  And match driver.url == webUrlBase + '/page-03'

  # get html for all elements that match css selector
  When def list = scriptAll('div#eg01 div', '_.innerHTML')
  Then match list == '#[4]'
  And match each list contains '@@data'

  # powerful wait designed for tabular results that take time to load
  When def list = waitForResultCount('div#eg01 div', 4)  
  Then match list == '#[4]'

  When def list = waitForResultCount('div#eg01 div', 4, '_.innerHTML')
  Then match list == '#[4]'
  And match each list contains '@@data'

  # get html for all elements that match xpath selector
  When def list = scriptAll('//option', '_.innerHTML')
  Then match list == '#[3]'
  And match each list contains 'Option'

  # get text for all elements that match css selector
  When def list = scriptAll('div#eg01 div', '_.textContent')
  Then match list == '#[4]'
  And match each list contains '@@data'

  # get text for all but only containing given text
  When def list = scriptAll('div#eg01 div', '_.textContent', function(x){ return x.contains('data2') })
  Then match list == ['@@data2@@']

  # get text for all elements that match xpath selector
  When def list = scriptAll('//option', '_.textContent')
  Then match list == '#[3]'
  And match each list contains 'Option'

  # get value for all elements that match css selector
  When def list = scriptAll("input[name='data2']", '_.value')
  Then match list == '#[3]'
  And match each list contains 'check'

  # get value for all elements that match xpath selector
  When def list = scriptAll("//input[@name='data2']", '_.value')
  Then match list == '#[3]'
  And match each list contains 'check'

  # select option with text
  Given select('select[name=data1]', '{}Option Two')
  And click('input[value=check2]')
  When submit().click('#eg02SubmitId')
  And match text('#eg01Data1') == 'option2'
  And match text('#eg01Data2') == 'check2'

  # select option containing text
  Given select('select[name=data1]', '{^}Two')
  And click('[value=check2]')
  And click('[value=check1]')
  When submit().click('#eg02SubmitId')
  And match text('#eg01Data1') == 'option2'
  And match text('#eg01Data2') == '["check1","check2"]'

  # select option by value
  Given select('select[name=data1]', 'option2')
  When submit().click('#eg02SubmitId')
  And match text('#eg01Data1') == 'option2'

  # friendly locators: leftOf / rightOf
  * leftOf('{}Check Three').click()
  * rightOf('{}Input On Right').input('input right')  
  * leftOf('{}Input On Left').clear().input('input left')
  * submit().click('#eg02SubmitId')
  * match text('#eg01Data2') == 'check3'
  * match text('#eg01Data3') == 'Some Textinput right'
  * match text('#eg01Data4') == 'input left'

  # friendly locators: above / below / near
  * near('{}Go to Page One').click()
  * below('{}Input On Right').input('input below')  
  * above('{}Input On Left').clear().input('input above')
  * submit().click('#eg02SubmitId')
  # TODO problem in safari
  # * match text('#eg01Data2') == 'check1'
  * match text('#eg01Data3') == 'input above'
  * match text('#eg01Data4') == 'Some Textinput below'

  # friendly locator find by visible text
  * above('{}Input On Right').find('{}Go to Page One').click()
  * waitForUrl('/page-01')

  # switch to iframe by index
  Given driver webUrlBase + '/page-04'
  And match driver.url == webUrlBase + '/page-04'
  # TODO problem with safari
  And switchFrame(config.type == 'safaridriver' ? '#frame01' : 0)
  When input('#eg01InputId', 'hello world')
  And click('#eg01SubmitId')
  Then match text('#eg01DivId') == 'hello world'

  # switch back to parent frame
  When switchFrame(null)
  Then match text('#eg01DivId') == 'this div is outside the iframe'

  # switch to iframe by locator
  Given driver webUrlBase + '/page-04'
  And match driver.url == webUrlBase + '/page-04'
  And switchFrame('#frame01')
  When input('#eg01InputId', 'hello world')
  And click('#eg01SubmitId')
  Then match text('#eg01DivId') == 'hello world'
  And switchFrame(null)

  # mouse move and click
  * mouse('#eg02LeftDivId').go()
  * mouse('#eg02RightDivId').click()
  * mouse().down().move('#eg02LeftDivId').up()
  * def temp = text('#eg02ResultDivId')
  # works only for chrome :(
  # * match temp contains 'LEFT_HOVERED'
  # * match temp contains 'RIGHT_CLICKED'
  # * match temp !contains 'LEFT_DOWN'
  # * match temp contains 'LEFT_UP'

Examples:
    | config | dimensions |
    | { type: 'chrome' } | { x: 0, y: 0, width: 300, height: 800 } |
    | { type: 'chromedriver' } | { x: 50, y: 0, width: 250, height: 800 } |
    | { type: 'geckodriver' } | { x: 600, y: 0, width: 300, height: 800 } |
    | { type: 'safaridriver' } | { x: 1000, y: 0, width: 400, height: 800 } |
    # | { type: 'mswebdriver' } |
    # | { type: 'msedge' } |
    