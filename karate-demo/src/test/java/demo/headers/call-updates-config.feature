Feature: a called feature can 'contribute' to variables and config 
    including headers and cookies

Background:
# the shape of the next line is important. if the line starts with call (or callonce)
# the called script will update the context here in this file.
# think of it as similar to an 'include' directive
* callonce read('common.feature')
* url demoBaseUrl

Scenario: no extra config - they have been set automatically by 'common.feature'

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

#Scenario: configure json
#
#    Given path 'headers', token
#    And param url = demoBaseUrl
#    When method get
#    Then status 200
