Feature:

Background:
* driver serverUrl + '/07'

Scenario:
* input('#inputId', 'hello world')
* click('input[name=submitName]')
* match value('#inputId') == 'hello world'
* match text('#valueId') == 'hello world'
* match html('#valueId') == '<div id="valueId">hello world</div>'
* def expected = 
"""
(driverType == 'safaridriver')
? '72d72u69d69u76d76u76d76u79d79u65d65u87d87u79d79u82d82u76d76u68d68u'
: '72d72u69d69u76d76u76d76u79d79u32d32u87d87u79d79u82d82u76d76u68d68u'
"""
* match text('#pressedId') == expected
