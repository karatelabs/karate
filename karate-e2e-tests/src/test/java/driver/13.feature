Feature:

Background:
  * driver serverUrl + '/13'

Scenario: try out iframe scenarios
  * match text("div#messageId") == "this div is outside the iframe"
  * waitFor("#myiframe")
  * switchFrame("#myiframe")
  ## matching Wikipedia page title
  ## hopefully won't change often :)
  * match driver.title == "Office Space - Wikipedia"
  * input("input[name='search']", "karate")
  * click("input[id='searchButton']")
  * waitForUrl('https://en.wikipedia.org/wiki/Karate')
  * match driver.title == "Karate - Wikipedia"
  * switchFrame(null)
  # trying the same thing with locate chained by switchFrame()
  * locate("iframe[id='myiframe']").switchFrame()
  * switchFrame(null)
  * switchFrame("#frameId")
  * input("input#inputId", "testing input")
  * click("input#submitId")
  * match text("div#valueId") == "testing input"
  * match optional("div#messageId").present == false
  * switchFrame(null)
  * match text("div#messageId") == "this div is outside the iframe"

  # maybe fix also solves for #1715
  * switchFrame("#iframe08")
  * switchFrame("#frameId")
  * input("input#inputId", "testing input")
  * click("input#submitId")
  * match text("div#valueId") == "testing input"
  * switchFrame(null)

    # * locate("iframe[name='myiframe']").switchFrame()
