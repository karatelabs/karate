Feature:

Scenario:
* configure driver = { type: 'chrome' }
* driver 'http://the-internet.herokuapp.com/upload'
* robot { window: '^Chrome', highlight: true }
# since we have the driver active, the "robot" namespace is needed
* robot.click('choose-file.png').delay(1000)
* robot.input('/Users/pthomas3/Desktop' + Key.ENTER)
* robot.click('file-name.png').input(Key.ENTER).delay(1000)
* submit().click('#file-submit')
* waitForText('#uploaded-files', 'billie.jpg')
* screenshot()
