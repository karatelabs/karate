Feature:

Background:
    * call read 'js-read-3.json'
    * def storedVar = call read 'js-read-3.json'

Scenario:
    * print 'step in Scenario to trigger background steps'
    #* karate.set('storedVar', storedVar)
    #* karate.set('storedVar', `call read 'js-read-3.json'`)
