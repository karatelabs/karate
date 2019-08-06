Feature: scratch pad 2

Scenario Outline: <type>
  * configure driver = { type: '#(type)', showDriverLog: true }

  Given driver 'https://semantic-ui.com/modules/dropdown.html'
  Then scroll('select[name=skills]').click()
  And click('div[data-value=css]')
  And click('div[data-value=html]')
  And click('div[data-value=ember]')
  And delay(1000).screenshot()

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |