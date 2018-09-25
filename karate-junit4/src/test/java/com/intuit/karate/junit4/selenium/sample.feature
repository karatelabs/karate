@ignore
Feature: no-name-default-test-suite
	url = https://github.com/
	with parameters
		session.id, session.url
		
Scenario: Test-1-of-Default-Suite-136573f0-2437-48aa-9bfe-457f0c88ee8b
# # TestCommand{id='a84bb8d4-b05a-4817-8cf6-457beaaba7cc', comment='', command='open', target='/', value=''}

* url 'http://localhost:9515'

* path 'session'
* request { desiredCapabilities: { browserName: 'Chrome' } }
* method post
* status 200
* def sessionId = response.sessionId

* def sessionPath = 'session/' + sessionId

* path sessionPath, 'url'
* request { url: 'https://google.com' }
* method post
* status 200

Given path sessionPath, 'url'
And request {url:'https://github.com/'}
When method POST
Then status 200
And assert response.status == 0

# # TestCommand{id='f4322a13-ab17-4961-b9b7-745dd42a2195', comment='', command='mouseOver', target='//div[@id='dashboard']/div/a', value=''}

# # TestCommand{id='bed92dae-1ea6-458b-b051-4b3666fa5b80', comment='', command='clickAt', target='name=q', value='93,24'}
Given path sessionPath, 'element'
And request {using:'name', value:'q'}
When method POST
Then status 200
And assert response.status == 0
* def webdriverElementId = response.value.ELEMENT
* print 'Element ID is 'webdriverElementId

Given path sessionPath, 'element', webdriverElementId, 'click'
And request {}
When method POST
Then status 200
And assert response.status == 0

# # TestCommand{id='a2bb0a5e-6967-40e7-8a67-516bfe380dba', comment='', command='store', target='karate', value='searchFor'}

# # TestCommand{id='641fc6bf-e1e1-4089-a6e7-884f849d0028', comment='', command='type', target='name=q', value='karate'}
Given path sessionPath, 'element'
And request {using:'name', value:'q'}
When method POST
Then status 200
And assert response.status == 0
* def webdriverElementId = response.value.ELEMENT
* print 'Element ID is 'webdriverElementId

Given path sessionPath, 'element', webdriverElementId, 'value'
And request {value:['karate']}
When method POST
Then status 200
And assert response.status == 0

# # TestCommand{id='81dbc283-9531-4005-8bd4-b4d3c95d6c81', comment='', command='sendKeys', target='name=q', value='${KEY_ENTER}'}
Given path sessionPath, 'element'
And request {using:'name', value:'q'}
When method POST
Then status 200
And assert response.status == 0
* def webdriverElementId = response.value.ELEMENT
* print 'Element ID is 'webdriverElementId

Given path sessionPath, 'element', webdriverElementId, 'value'
And request {value:['${KEY_ENTER}']}
When method POST
Then status 200
And assert response.status == 0
