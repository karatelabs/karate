Feature: scratch pad 2

Scenario Outline: <type>
  * configure driver = { type: '#(type)', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']

  Given driver webUrlBase + '/page-01'
  And input('#eg02InputId', Key.CONTROL + 'a')
  And def temp = text('#eg02DivId')
  And match temp contains '17d'
  And match temp contains '65u'
  And script('#eg02DivId', "_.innerHTML = ''")
  When input('#eg02InputId', 'aa')
  Then def temp = text('#eg02DivId')
  And match temp contains '65u'
  And input('#eg01InputId', 'hello world')
  When click('input[name=eg01SubmitName]')
  And match value('#eg01InputId') == 'hello world'
  Then match text('#eg01DivId') == 'hello world'
  And match attribute('#eg01SubmitId', 'type') == 'submit'
  And match name('#eg01SubmitId') == 'INPUT'
  And match enabled('#eg01InputId') == true
  And match enabled('#eg01DisabledId') == false

Examples:
| type         |
| chrome       |
| chromedriver |
| geckodriver  |
| safaridriver |