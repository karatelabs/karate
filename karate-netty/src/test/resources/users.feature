Feature: sample karate script that calls a live www web-service

Background:
* url 'https://jsonplaceholder.typicode.com'

Scenario: get users and then get first by id

Given path 'users'
When method get
Then status 200

* def first = response[0]

Given path 'users', first.id
When method get
Then status 200

Scenario: create a user and then get it by id

* def user =
"""
{
    "name": "Test User",
    "username": "testuser",
    "email": "test@user.com",
    "address": {
      "street": "Has No Name",
      "suite": "Apt. 123",
      "city": "Electri",
      "zipcode": "54321-6789"
    }
}
"""

Given url 'https://jsonplaceholder.typicode.com/users'
And request user
When method post
Then status 201

* def id = response.id
* print 'created id is: ' + id
