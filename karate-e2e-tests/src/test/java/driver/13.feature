Feature:

Background:
  * driver serverUrl + '/13'

Scenario: open frame
  * match text("div#messageId") == "this div is outside the iframe"
  * waitFor("#myiframe")
  * switchFrame("#myiframe")
  * switchFrame(null)
  * locate("iframe[id='myiframe']").switchFrame()
  * switchFrame(null)
  * switchFrame("#frameId")
  * input("input#inputId", "testing input")
  * click("input#submitId")
  * match text("div#valueId") == "testing input"
  * match optional("div#messageId").present == false
  * switchFrame(null)
  * match text("div#messageId") == "this div is outside the iframe"


    # * locate("iframe[name='myiframe']").switchFrame()
