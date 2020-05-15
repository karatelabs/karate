Feature: scratch pad 2

Scenario Outline: <type>
  * def webUrlBase = karate.properties['web.url.base']
  * configure driver = { type: '#(type)', showDriverLog: true }

  * driver webUrlBase + '/page-02'

* highlightAll('a')
* def elements = locateAll('{}Click Me')
* match karate.sizeOf(elements) == 7
* elements[6].click()
* match elements[3].script('_.tagName') == 'BUTTON'
  

Examples:
| type         |
| chrome       |
#| chromedriver |
#| geckodriver  |
#| safaridriver |