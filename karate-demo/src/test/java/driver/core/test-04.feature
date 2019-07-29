Feature: scratch pad 2

Scenario Outline: <type>
  * configure driver = { type: '#(type)', showDriverLog: true }
  * def webUrlBase = karate.properties['web.url.base']

  Given driver webUrlBase + '/page-01'
  And input('#eg02InputId', Key.CONTROL + 'a')
  Then match text('#eg02DivId') contains '17d65d'
  And script('#eg02DivId', "_.innerHTML = ''")
  And input('#eg02InputId', 'aa')
  Then match text('#eg02DivId') contains '65d65u'
  And input('#eg01InputId', 'hello world')
  When click('input[name=eg01SubmitName]')
  And match value('#eg01InputId') == (type == 'safaridriver' ? '' : 'hello world')
  Then match text('#eg01DivId') == (type == 'safaridriver' ? '' : 'hello world')
  And match attribute('#eg01SubmitId', 'type') == 'submit'
  And match name('#eg01SubmitId') == 'INPUT'
  And match enabled('#eg01InputId') == true
  And match enabled('#eg01DisabledId') == false

Examples:
| type         |
| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |