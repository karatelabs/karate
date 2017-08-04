Feature: call multiple scenarios but use a common validation routine

Background:
* def types = ['json', 'xml'];
* def cat = { name: 'Tom' }
* def fun = read('loop.js')

Scenario: main loop
* def response = fun(types, cat)
* assert response.length == 2
* match each response == { id: '#number', name: 'Tom' }
* match each response contains cat
* match response == '#[2] ^cat'
