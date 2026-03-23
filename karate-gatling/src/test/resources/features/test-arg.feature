Feature: Test __arg and __gatling accessibility

  Background:
    * def baseUrl = 'http://localhost:8080'
    * url baseUrl

  Scenario: Verify __arg and __gatling are accessible with Background
    # Print __arg to see what we received
    * print '__arg:', __arg
    * print '__gatling:', __gatling

    # Access __gatling.name directly
    * def name = __gatling.name
    * print 'name:', name

    # Assert it has the expected value
    * match name == 'TestKitty'
