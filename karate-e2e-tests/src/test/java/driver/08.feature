Feature: 

Background:
* driver serverUrl + '/08'

Scenario: using css selector
* match text('#messageId') == 'this div is outside the iframe'
* switchFrame('#frameId')
* input('#inputId', 'hello world')
* click('input[name=submitName]')
* match value('#inputId') == 'hello world'
* match text('#valueId') == 'hello world'

# switch back to parent frame
* switchFrame(-1)
* match text('#messageId') == 'this div is outside the iframe'

Scenario: using frame index
* match text('#messageId') == 'this div is outside the iframe'
* switchFrame(0)
* input('#inputId', 'hello world')
* click('input[name=submitName]')
* match value('#inputId') == 'hello world'
* match text('#valueId') == 'hello world'

# switch back to parent frame
* switchFrame(-1)
* match text('#messageId') == 'this div is outside the iframe'

Scenario: upload within frame
* if (driverType != 'chrome') karate.abort()
* switchFrame('#uploadFrameId')
* driver.inputFile('#fileToUpload', '08.pdf')
* click('#uploadButton')
* waitForText('#uploadMessage', '08.pdf')
