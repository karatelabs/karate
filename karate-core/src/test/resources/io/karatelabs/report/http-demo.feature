Feature: HTTP API Demo

  Demonstrates HTTP requests in reports with request/response logging.

  Background:
    * url baseUrl
    * print 'Testing against:', baseUrl

  @http @smoke
  Scenario: Get all users
    Given path 'api/users'
    When method get
    Then status 200
    And match response.users == '#[2]'
    And match response.users[0].name == 'Alice'
    And match response.total == 2

  @http @smoke
  Scenario: Get single user by ID
    Given path 'api/users/42'
    When method get
    Then status 200
    And match response.id == 42
    And match response.name == 'User 42'
    And match response.active == true

  @http @create
  Scenario: Create a new user
    Given path 'api/users'
    And request { name: 'Charlie', email: 'charlie@example.com' }
    When method post
    Then status 201
    And match response.created == true
    And match response.name == 'Charlie'
    * print 'Created user with ID:', response.id

  @http @health
  Scenario: Check API health status
    Given path 'api/status'
    When method get
    Then status 200
    And match response.status == 'healthy'
    And match response.version == '2.0.0'
    * karate.embed(response, 'application/json', 'Health Check Response')

  @http @headers
  Scenario: Request with custom headers
    Given path 'api/users'
    And header Accept = 'application/json'
    And header X-Request-ID = 'test-123'
    When method get
    Then status 200
    * print 'Response received with', response.total, 'users'
