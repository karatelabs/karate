Feature: a karate test using cucumber scenario outlines

Background:
Given url mockServerUrl + 'dogs'

Scenario Outline: create many dogs

Given request { name: '<name>', age: <age>, height: <height> }
When method post
Then status 201
# And match response == { id: '#ignore', name: '<name>', age: <age>, height: <height> }

Given path response.id
When method get
Then status 200

Examples:
|  name  | age | height |
| Snoopy |  2  | 12.1   |
| Pluto  |  5  | 20.2   |
| Scooby | 10  | 40.7   |
| Spike  |  7  | 35.3   |
