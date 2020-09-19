Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true, showProcessLog: true }

  * driver webUrlBase + '/page-02'

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
  * dialog(true)
  When click('{}Show Alert')
  Then match driver.dialog == 'this is an alert'

  # dialog - confirm true
  * dialog(false)
  When click('{}Show Confirm')
  Then match driver.dialog == 'this is a confirm'
  And match text('#eg02DivId') == 'Cancel'

  # dialog - confirm false
  * dialog(true)
  When click('{}Show Confirm')
  And match text('#eg02DivId') == 'OK'

  # dialog - prompt
  * dialog(true, 'hello world')
  When click('{}Show Prompt')
  Then match driver.dialog == 'this is a prompt'
  And match text('#eg02DivId') == 'hello world'

  # screenshot of selected element
  * screenshot('#eg02DivId')

  # get element dimensions
  * match position('#eg02DivId') contains { x: '#number', y: '#number', width: '#number', height: '#number' }


Examples:
| type         |
#| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |
| playwright |