Feature:

Background:
    * call read 'js-read-5.json'
    * def storedVar2 = call read 'js-read-5.json'

Scenario:
    * print 'step in Scenario to trigger background steps'
    #* karate.set('storedVar', storedVar)
    #* karate.set('storedVar', `call read 'js-read-3.json'`)
