@ignore
Feature: common routine that updates the configured headers and cookies
    and has multiple steps where the headers are set-up by the first step

Scenario:
Given url demoBaseUrl
And path 'headers'
When method get
Then status 200

# this is the line that has to be duplicated in the 'caller' feature
* configure headers = read('classpath:headers.js')

# and these variables need to be 'unpacked' by the 'caller' as well
* def time = responseCookies.time.value
* def token = response

# all because we are not using 'shared scope'
# and this second API call depends on the 'headers.js' configured above
Given path 'headers', token
And param url = demoBaseUrl
When method get
Then status 200
