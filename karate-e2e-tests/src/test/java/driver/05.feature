Feature:

Background:
* driver serverUrl + '/05'

Scenario:
* url serverUrl + '/api/05'
* method get
* match response == { message: 'hello world' }

* click('button')
* waitForText('#containerDiv', 'hello world')

* driver.intercept({ patterns: [{ urlPattern: '*/api/*' }], mock: '05_mock.feature' })

* click('button')
* waitForText('#containerDiv', 'hello faked')