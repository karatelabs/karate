Feature:

Background:
* driver serverUrl + '/15'

Scenario:
# simple xpath
* def list = scriptAll('//p', '_.textContent')
* match list == ['First', 'Second']
