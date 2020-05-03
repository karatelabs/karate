Feature:

Scenario:
* configure driver = { type: 'chrome' }
* driver 'http://the-internet.herokuapp.com/upload'
* robot { app: '^Chrome', highlight: true }
* robot.click('choose-file.png').delay(1000)
* robot.input('/Users/pthomas3/Desktop' + Key.ENTER)
* robot.click('file-name.png').input(Key.ENTER).delay(1000)
* submit().click('#file-submit')
* waitForText('#uploaded-files', 'billie.jpg')
* screenshot()
