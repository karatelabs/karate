Feature: User Management API

Background:
# This background runs before each scenario
* print 'setting up test context'

@smoke @critical @auth
Scenario: User login succeeds with valid credentials
# Setup test credentials for authentication
* def credentials = { username: 'admin', password: 'secret' }
# Simulate API response with token
* def response = { token: 'abc123', expires: 3600 }
# Verify token is returned correctly
* match response.token == 'abc123'
* print 'login successful'

@smoke @regression
Scenario: User profile returns correct data
* def user = { id: 1, name: 'John Doe', email: 'john@example.com', role: 'admin' }
# This is a multi-line comment example
# Line 2: User object contains profile information
# Line 3: We verify all key fields are correct
* match user.name == 'John Doe'
* match user.role == 'admin'

@regression @security
Scenario: Unauthorized access is rejected
* def response = { status: 401, message: 'Unauthorized' }
* match response.status == 401

@wip
Scenario: This test is still in progress
* def a = 1
* match a == 2

@wip @match-failures
Scenario: Multiple match failures in nested JSON
* def actual =
"""
{
  "user": {
    "id": 123,
    "name": "John",
    "email": "john@wrong.com",
    "profile": {
      "age": 25,
      "city": "Boston",
      "settings": {
        "theme": "dark",
        "notifications": false
      }
    }
  },
  "metadata": {
    "version": "1.0",
    "timestamp": "2024-01-15"
  }
}
"""
# user profile should match expected values
* match actual ==
"""
{
  "user": {
    "id": 456,
    "name": "Jane",
    "email": "jane@example.com",
    "profile": {
      "age": 30,
      "city": "Boston",
      "settings": {
        "theme": "light",
        "notifications": true
      }
    }
  },
  "metadata": {
    "version": "2.0",
    "timestamp": "2024-01-15"
  }
}
"""

@wip @match-failures
Scenario: Array match failures at multiple positions
* def items = [{id: 1, name: 'apple', price: 1.50}, {id: 2, name: 'banana', price: 0.75}, {id: 3, name: 'cherry', price: 2.00}]
# all items should have correct prices
* match items ==
"""
[
  {id: 1, name: 'apple', price: 1.99},
  {id: 2, name: 'orange', price: 0.75},
  {id: 3, name: 'cherry', price: 3.50}
]
"""

@wip @match-failures
Scenario: Contains deep failure with nested objects
* def response = { data: { users: [{ id: 1, role: 'user' }, { id: 2, role: 'admin' }] } }
# response should contain expected nested structure
* match response contains deep { data: { users: [{ id: 1, role: 'admin' }, { id: 2, role: 'guest' }] } }

@slow @integration
Scenario: Database sync completes successfully
* def result = 'sync complete'
* match result == 'sync complete'
* print 'database synced'

@embed @demo
Scenario: Embedded content showcase
* def apiResponse = { status: 'ok', users: [{id: 1, name: 'Alice'}, {id: 2, name: 'Bob'}] }
* karate.embed(apiResponse, 'application/json', 'API Response')
* karate.embed('Test completed at ' + java.time.LocalDateTime.now(), 'text/plain', 'Timestamp')
* karate.embed('<div style="padding:10px;background:#e8f5e9;border-radius:4px"><h3>Test Result</h3><p>All assertions passed!</p></div>', 'text/html', 'Result Summary')
* match apiResponse.status == 'ok'

@call @nested
Scenario: Single nested call example
* print 'about to call helper feature'
* def result = call read('helper.feature')
* print 'helper call completed'
* match result.user.name == 'Test User'

@call @nested
Scenario: Multiple nested calls
* print 'starting multi-call test'
* call read('data-setup.feature')
* def helperResult = call read('helper.feature')
* print 'both calls completed'
* match helperResult.valid == true

@call @nested @loop
Scenario: Loop call with array data
* def testCases = [{name: 'Case A', value: 1}, {name: 'Case B', value: 2}, {name: 'Case C', value: 3}]
* print 'running', testCases.length, 'test cases'
* def results = call read('data-setup.feature') testCases
* print 'loop call completed'
