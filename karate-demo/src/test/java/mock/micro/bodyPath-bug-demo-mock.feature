Feature: demo bodyPath bug by launching the mock server with CLI: 
        java -jar karate-0.9.6.jar -m cats-mock.feature -p 8090

Background: Please download karate-0.9.6.jar

# bodyPath bug demo
Scenario: pathMatches('/') && methodIs('post') && typeContains('xml')
    * def descriptionPath = '/Envelope/Body/CreatePayment/request/Description'
    * print "descriptionPath", descriptionPath
    * def description =  bodyPath(descriptionPath) == null ? null : bodyPath(descriptionPath)
    * print "description  = ", description

Scenario:
   * def responseStatus = 404
   * def response =  { 'ErrorMessage': 'Not Found' }
