Feature:

Scenario:
* configure driver = { type: 'chrome' }
* driver 'http://the-internet.herokuapp.com/upload'
* driver.inputFile('#file-upload', '../../demo/upload/test.pdf')
* submit().click('#file-submit')
* waitForText('#uploaded-files', 'test.pdf')
* screenshot()

