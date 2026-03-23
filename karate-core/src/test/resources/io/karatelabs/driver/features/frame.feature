Feature: Frame Tests
  Frame switching operations

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/iframe'
    # Ensure we're in the main frame
    * switchFrame(null)

  # ========== Main Frame Operations ==========

  Scenario: Main frame content
    * def title = text('h1')
    * match title == 'IFrame Test Page'
    * def mainText = text('#main-text')
    * match mainText == 'This content is in the main frame.'

  Scenario: Main frame variable
    * def value = script('window.mainValue')
    * match value == 'main-data'

  Scenario: Main frame button
    * click('#main-btn')
    * waitForText('#result', 'Main button clicked!')
    * def resultText = text('#result')
    * match resultText == 'Main button clicked!'

  # ========== Switch Frame by Index ==========

  Scenario: Switch frame by index
    * switchFrame(0)
    * def frameText = text('#frame-text')
    * match frameText == 'This content is inside the iframe.'

  Scenario: Frame variable by index
    * switchFrame(0)
    * def value = script('window.frameValue')
    * match value == 'iframe-data'

  Scenario: Frame button by index
    * switchFrame(0)
    * click('#frame-btn')
    * waitForText('#frame-result', 'Frame button clicked!')
    * def resultText = text('#frame-result')
    * match resultText == 'Frame button clicked!'

  # ========== Switch Frame by Locator ==========

  Scenario: Switch frame by ID locator
    * switchFrame('#test-frame')
    * def frameText = text('#frame-text')
    * match frameText == 'This content is inside the iframe.'

  Scenario: Switch frame by name locator
    * switchFrame("iframe[name='testFrame']")
    * def heading = text('h2')
    * match heading == 'Frame Content'

  Scenario: Frame content by locator
    * switchFrame('#test-frame')
    * match exists('#frame-btn') == true
    * match exists('#frame-result') == true

  # ========== Switch Back to Main Frame ==========

  Scenario: Switch back to main frame
    * switchFrame(0)
    * def frameValue = script('window.frameValue')
    * match frameValue == 'iframe-data'
    * switchFrame(null)
    * def mainValue = script('window.mainValue')
    * match mainValue == 'main-data'

  Scenario: Main frame content after switch
    * switchFrame('#test-frame')
    * switchFrame(null)
    * def title = text('h1')
    * match title == 'IFrame Test Page'
    * match exists('#main-btn') == true
    * match exists('#main-text') == true

  # ========== Frame Operations ==========

  Scenario: Element operations in frame
    * switchFrame('#test-frame')
    * match exists('#frame-text') == true
    * match exists('#frame-btn') == true
    * def text = text('#frame-text')
    * match text == 'This content is inside the iframe.'
    * def html = html('#frame-text')
    * match html contains 'This content is inside the iframe.'

  Scenario: Click and result in frame
    * switchFrame('#test-frame')
    * def initialResult = text('#frame-result')
    * match initialResult == ''
    * click('#frame-btn')
    * waitForText('#frame-result', 'Frame button clicked!')

  Scenario: Script execution in frame
    * switchFrame('#test-frame')
    * def value = script("document.getElementById('frame-text').textContent")
    * match value == 'This content is inside the iframe.'

  Scenario: Frame does not affect main frame
    * switchFrame('#test-frame')
    * click('#frame-btn')
    * waitForText('#frame-result', 'Frame button clicked!')
    * switchFrame(null)
    * def mainResult = text('#result')
    * match mainResult !contains 'Frame button'

  # ========== Multiple Frame Switches ==========

  Scenario: Multiple switches
    * def mainValue = script('window.mainValue')
    * match mainValue == 'main-data'
    * switchFrame(0)
    * def frameValue = script('window.frameValue')
    * match frameValue == 'iframe-data'
    * switchFrame(null)
    * def mainValue = script('window.mainValue')
    * match mainValue == 'main-data'
    * switchFrame('#test-frame')
    * def frameValue = script('window.frameValue')
    * match frameValue == 'iframe-data'
    * switchFrame(null)
