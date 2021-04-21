Feature:

Background:
* def uuid = function(){ return java.util.UUID.randomUUID() + '' }

Scenario: pathMatches('/test')
* def response = { success: true }
