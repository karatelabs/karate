Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-03'

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
  * match text('#eg01Data2') == 'check1'
  * match text('#eg01Data3') == 'input above'
  * match text('#eg01Data4') == 'Some Textinput below'

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |