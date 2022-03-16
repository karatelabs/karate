Feature:

Background:
  * driver serverUrl + '/14'

Scenario: try out nested iframe scenarios
  # switch into nested iframe and fill out form
  * switchFrame("#nestedParent")
  * waitFor("#nestedChild")
  * switchFrame("#nestedChild")
  * waitFor("input[name='search']")
  * input("input[name='search']", "hello world")

  # switch back to root frame and click "continue"
  * switchFrame(null)
  * click('#continueButton')

  # wait for "processing" => nested iframe is replaced and is then removed in background
  * waitForText('body', 'Success')

  # after successful processing we'll get a "finish button" that creates our final state
  * click('#finish')
  * match html('#result') == '<p id="result">Done</p>'
