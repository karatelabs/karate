Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-02'

  # find all
  * def elements = findAll('{}Click Me')
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

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |