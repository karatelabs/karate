Feature:

Scenario:
* def urlBase = 'http://localhost:' + karate.properties['server.port']
* call read('call-response-called.feature')
* match responseTime == '#number'

