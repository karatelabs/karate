@ignore
Feature: common routine that updates the configured headers and cookies

Scenario:
Given url demoBaseUrl
And path 'headers'
When method get
Then status 200

* def time = responseCookies.time.value
* def token = response
# cookies are auto-configured, i.e. they 'persist' for subsequent HTTP calls

# if you are using 'shared scope': https://github.com/intuit/karate#shared-scope
# this next line will update the global scope, which is the recommended approach for re-usable sign-in / auth flows
* configure headers = read('classpath:headers.js')

# if you have more HTTP / API calls as part of this 're-usable' sign-in flow
# they can be made here, and they will use the 'headers.js' configured above

# if you are NOT using 'shared scope', you will need to duplicate the
# 'configure headers' line in your 'caller' feature for your main flow to work
# and ensure that the 'time' and 'token' variables are returned from here 
# and set (using 'def') in the 'caller' feature, including cookies if needed

# refer to 'call-isolated-headers.feature' and 'common-multiple.feature'
# for an example of NOT using 'shared scope'
