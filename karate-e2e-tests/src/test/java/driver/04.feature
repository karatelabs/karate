Feature:

Background:
* driver serverUrl + '/04'

Scenario:
# foo=bar is set by the server
* def cookie1 = { name: 'foo', value: 'bar' }
And match driver.cookies contains deep cookie1
And match cookie('foo') contains deep cookie1

* def cookie2 = { name: 'hello', value: 'world' }
* cookie(cookie2)
* match driver.cookies contains deep cookie2

# delete cookie
* deleteCookie('foo')
* match driver.cookies !contains '#(^cookie1)'

# clear cookies
* clearCookies()
* match driver.cookies == '#[0]'

# set multiple cookies at once e.g. from an API call
* def data = [{ name: 'one', value: '1' }, { name: 'two', value: '2' }]
* driver.cookies = data
* match driver.cookies contains deep data

