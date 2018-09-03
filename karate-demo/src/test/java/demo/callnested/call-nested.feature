Feature: calling a feature file which calls another feature file
    this is not really recommended and this demo is just to test karate / reporting

Background:
* url demoBaseUrl

Scenario: calling a feature with parameters
    * print 'in main caller'
    * call read('called1.feature')
