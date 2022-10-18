Feature:

Background:
* driver serverUrl + '/07'

Scenario:
* input('#inputId', 'hello world')
* click('input[name=submitName]')
* match value('#inputId') == 'hello world'
* match text('#valueId') == 'hello world'
* match html('#valueId') == '<div id="valueId">hello world</div>'
* def expected = '72d72u69d69u76d76u76d76u79d79u32d32u87d87u79d79u82d82u76d76u68d68u'
* if (driverType == 'chrome') expected = '104d104u101d101u108d108u108d108u111d111u32d32u119d119u111d111u114d114u108d108u100d100u'
* if (driverType == 'safaridriver') expected = '72d72u69d69u76d76u76d76u79d79u65d65u87d87u79d79u82d82u76d76u68d68u'
* match text('#pressedId') == expected

* clear('#inputId')
* waitFor('#inputId').input('hello world')
* waitFor('input[name=submitName]').click()
* match value('#inputId') == 'hello world'
* match text('#valueId') == 'hello world'
* match html('#valueId') == '<div id="valueId">hello world</div>'
