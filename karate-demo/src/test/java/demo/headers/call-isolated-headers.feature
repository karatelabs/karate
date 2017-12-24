Feature: when not using 'shared scope', 'configure headers' may need to be
    duplicated even in 'called' features and the variables needed for the
    headers JS routine should be returned and made available to the 'caller'

Background:
# note how this next line has to be duplicated in 'common-multiple.feature'
* configure headers = read('classpath:headers.js')

* def setup = callonce read('common-multiple.feature')
# and we have to ensure the 'time' and 'token' variables are set for 'headers.js' to work
* def time = setup.time
* def token = setup.token

# a cookie is also needed in our authentication demo example
* cookie time = setup.time

# for an example of using 'shared scope' which simplifies the above
# refer to 'call-updates-config.feature' and 'common.feature'

* url demoBaseUrl

Scenario: all auth headers have been set via the background and 'common-multiple.feature'
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: make sure that the second scenario also works well with the 'callonce' in the background
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200
