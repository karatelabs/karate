Feature:

Background:
* driver serverUrl + '/03'

Scenario:
# different ways to use waitUntil()
* waitUntil('#helloDiv', "function(e){ return e.innerHTML == 'hello world' }")
* waitUntil('#helloDiv', "_.innerHTML == 'hello world'")
* waitUntil('#helloDiv', '!_.disabled')

# different ways to use script()
* match script('#helloDiv', "function(e){ return e.innerHTML }") == 'hello world'
* match script('#helloDiv', '_.innerHTML') == 'hello world'
* match script('#helloDiv', '!_.disabled') == true
* match script('#helloDiv', "_.style['color']") == 'red'
* match script('.styled-div', "function(e){ return getComputedStyle(e)['font-size'] }") == '30px'
* match script('.styled-div', "_ => getComputedStyle(_)['font-size']") == '30px'